package com.mallsen.controller.aigc;

import com.mallsen.context.BaseContext;
import com.mallsen.properties.JwtProperties;
import com.mallsen.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户端 AI 智能体接口（角色固定为 user，由用户 JWT 拦截器鉴权）
 * 安全要点：
 * 1. role 由服务端固定为 user，客户端传入的 role 一律忽略；
 * 2. userId 一律取自已认证的用户 JWT（BaseContext），忽略客户端传值，防止越权代下单；
 * 3. 会话按 userId 隔离，sessionId 由服务端生成 user_{userId}，避免跨用户共享对话历史；
 * 4. 将用户 JWT 透传给 Python Agent，使其回调用 Java 时携带用户身份（/user/business/ai/** 均校验归属）。
 */
@RestController
@RequestMapping("/user/aigc")
@Tag(name = "用户 AI 智能体接口")
@Slf4j
@RequiredArgsConstructor
public class UserAigcChatController {

    private static final String ROLE = "user";

    private final WebClient agentWebClient;
    private final JwtProperties jwtProperties;

    @PostMapping("/chat")
    @Operation(summary = "与用户智能客服对话")
    public Result<?> chat(@RequestBody Map<String, String> request, HttpServletRequest httpServletRequest) {
        Map<String, String> body = buildForwardBody(request, httpServletRequest);

        try {
            Map reply = agentWebClient.post()
                    .uri("/agent/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return Result.success(reply);
        } catch (Exception e) {
            log.error("调用智能体服务失败", e);
            return Result.error("智能体服务暂时不可用: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "与用户智能客服对话（流式输出）")
    public SseEmitter chatStream(@RequestBody Map<String, String> request, HttpServletRequest httpServletRequest) {
        Map<String, String> body = buildForwardBody(request, httpServletRequest);
        String sessionId = body.get("session_id");

        SseEmitter emitter = new SseEmitter(180_000L);

        agentWebClient.post()
                .uri("/agent/chat/stream")
                .bodyValue(body)
                .exchangeToFlux(response -> {
                    if (response.statusCode() != HttpStatus.OK) {
                        return Flux.error(new RuntimeException("agent返回错误状态: " + response.statusCode()));
                    }
                    return response.bodyToFlux(DataBuffer.class);
                })
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    ByteBuffer bb = buffer.toByteBuffer(buffer.readPosition(), bytes.length);
                    bb.get(bytes);
                    DataBufferUtils.release(buffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .subscribe(
                        chunk -> processChunk(chunk, emitter),
                        error -> {
                            log.error("流式调用智能体失败", error);
                            sendError(emitter, sessionId);
                            try { emitter.complete(); } catch (Exception ignored) {}
                        },
                        () -> {
                            try { emitter.complete(); } catch (Exception ignored) {}
                        }
                );

        return emitter;
    }

    /**
     * 构造转发给 Python Agent 的请求体：
     * role 固定 user、userId 取自已认证的 JWT、会话按 userId 隔离、透传用户令牌
     */
    private Map<String, String> buildForwardBody(Map<String, String> request, HttpServletRequest httpServletRequest) {
        Long userId = BaseContext.getCurrentId();
        String token = httpServletRequest.getHeader(jwtProperties.getUserTokenName());

        Map<String, String> body = new HashMap<>();
        body.put("message", request.get("message"));
        body.put("session_id", "user_" + userId);
        body.put("role", ROLE);
        body.put("user_id", String.valueOf(userId));
        body.put("token", token == null ? "" : token);
        return body;
    }

    private void processChunk(String chunk, SseEmitter emitter) {
        try {
            for (String line : chunk.split("\n")) {
                if (line.startsWith("data:")) {
                    String jsonData = line.substring(5).trim();
                    if (!jsonData.isEmpty() && !"[DONE]".equals(jsonData)) {
                        emitter.send(SseEmitter.event().data(jsonData, new MediaType("text", "plain", StandardCharsets.UTF_8)));
                    }
                }
            }
        } catch (IOException e) {
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    private void sendError(SseEmitter emitter, String sessionId) {
        try {
            String errorJson = "{\"token\":\"抱歉，服务暂时不可用。\",\"session_id\":\"" + sessionId + "\"}";
            emitter.send(SseEmitter.event().data(errorJson, new MediaType("text", "plain", StandardCharsets.UTF_8)));
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        }
    }
}

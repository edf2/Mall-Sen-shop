package com.mallsen.controller.aigc;

import com.mallsen.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
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
 * 商家端 AI 智能体接口（角色固定为 admin，由管理员 JWT 拦截器鉴权）
 * 该入口仅允许已登录的管理员访问，role 由服务端确定，客户端不可指定。
 */
@RestController
@RequestMapping("/admin/aigc")
@Tag(name = "商家 AI 智能体接口")
@Slf4j
@RequiredArgsConstructor
public class AigcController {

    private static final String ROLE = "admin";

    private final WebClient agentWebClient;

    @PostMapping("/chat")
    @Operation(summary = "与商家智能体对话")
    public Result<?> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String sessionId = request.getOrDefault("sessionId", "admin_default");

        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        body.put("session_id", sessionId);
        body.put("role", ROLE);

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
    @Operation(summary = "与商家智能体对话（流式输出）")
    public SseEmitter chatStream(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String sessionId = request.getOrDefault("sessionId", "admin_default");
        log.info("调用商家智能体(流式): message={}, sessionId={}", message, sessionId);

        SseEmitter emitter = new SseEmitter(180_000L);

        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        body.put("session_id", sessionId);
        body.put("role", ROLE);

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

    @GetMapping("/sessions")
    @Operation(summary = "查询商家智能体会话列表")
    public Object listSessions(@RequestParam(defaultValue = "admin") String role,
                               @RequestParam(defaultValue = "50") int limit,
                               @RequestParam(defaultValue = "0") int offset) {
        try {
            // 原样透传 Python 服务的响应体（{code, data}），保持前端契约不变
            return agentWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/agent/sessions")
                            .queryParam("role", role)
                            .queryParam("limit", limit)
                            .queryParam("offset", offset)
                            .build())
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
        } catch (Exception e) {
            log.error("查询会话列表失败", e);
            return Result.error("智能体服务暂时不可用: " + e.getMessage());
        }
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "查询商家智能体会话消息")
    public Object getSessionMessages(@PathVariable String sessionId) {
        try {
            // 原样透传 Python 服务的响应体（{code, data}），保持前端契约不变
            return agentWebClient.get()
                    .uri("/agent/sessions/{sessionId}/messages", sessionId)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();
        } catch (Exception e) {
            log.error("查询会话消息失败", e);
            return Result.error("智能体服务暂时不可用: " + e.getMessage());
        }
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

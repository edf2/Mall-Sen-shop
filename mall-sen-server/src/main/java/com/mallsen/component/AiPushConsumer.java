package com.mallsen.component;

import com.alibaba.fastjson.JSON;
import com.mallsen.message.AiPredictionMessage;
import com.mallsen.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiPushConsumer {

    private final WebSocketServer webSocketServer;

    @RabbitListener(queues = "ai.push.queue")
    public void handleAiPush(AiPredictionMessage msg) {
        log.info("收到AI预测推送: 共 {} 个预警", msg.getWarningCount());

        Map<String, Object> map = new HashMap<>();
        map.put("type", 4);
        map.put("time", msg.getTime().toString());
        map.put("warningCount", msg.getWarningCount());
        map.put("predictions", msg.getPredictions());

        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }
}

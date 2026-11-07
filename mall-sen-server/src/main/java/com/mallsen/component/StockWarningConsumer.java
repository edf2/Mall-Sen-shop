package com.mallsen.component;

import com.alibaba.fastjson.JSON;
import com.mallsen.message.StockWarningMessage;
import com.mallsen.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class StockWarningConsumer {

    @Autowired
    private WebSocketServer webSocketServer;

    @RabbitListener(queues = "stock.warning.queue")
    public void handleStockWarning(StockWarningMessage msg) {
        log.warn("库存预警 - 商品ID: {}, 商品名称: {}, 当前库存: {}, 预警阈值: {}",
                msg.getProductId(), msg.getProductName(), msg.getCurrentStock(), msg.getLowStockWarning());

        Map<String, Object> map = new HashMap<>();
        map.put("type", 3);
        map.put("productId", msg.getProductId());
        map.put("productName", msg.getProductName());
        map.put("currentStock", msg.getCurrentStock());
        map.put("lowStockWarning", msg.getLowStockWarning());
        map.put("time", msg.getTime());

        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }
}

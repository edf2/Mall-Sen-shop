package com.mallsen.component;

import com.alibaba.fastjson.JSON;
import com.mallsen.entity.MqOutbox;
import com.mallsen.mapper.MqOutboxMapper;
import com.mallsen.message.OrderAsyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MQ Outbox 投递器：从 mq_outbox 表拉取待发送消息并投递到 RabbitMQ。
 * <p>
 * 支付确认与订单状态更新同事务写入 outbox，投递失败时保持 status=0（待发送）状态，
 * 由 {@code MqOutboxRelayTask} 每5秒持续重试，保证 at-least-once；
 * 消费端（OrderAsyncConsumer）已有 Redis SETNX 幂等，重复投递不会重复累加销量。
 * <p>
 * 重试策略：失败次数 < {@link #MAX_RETRY_COUNT} 时保持 status=0 继续重试；
 * 失败次数 >= {@link #MAX_RETRY_COUNT} 时标记为 FAILED（status=2）并记录错误日志。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MqOutboxRelayer {

    public static final String ORDER_ASYNC_EXCHANGE = "order.async.exchange";
    public static final String ORDER_ASYNC_ROUTING_KEY = "order.async";

    /** 最大重试次数，超过后标记为失败 */
    private static final int MAX_RETRY_COUNT = 10;

    private final MqOutboxMapper mqOutboxMapper;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 拉取一批待发送消息并投递，返回本轮处理的条数。
     *
     * @param batchSize 单批处理条数
     */
    public int relayPending(int batchSize) {
        List<MqOutbox> pending = mqOutboxMapper.selectPending(batchSize);
        for (MqOutbox outbox : pending) {
            try {
                OrderAsyncMessage msg = JSON.parseObject(outbox.getPayload(), OrderAsyncMessage.class);
                rabbitTemplate.convertAndSend(ORDER_ASYNC_EXCHANGE, ORDER_ASYNC_ROUTING_KEY, msg);
                mqOutboxMapper.markSent(outbox.getId());
            } catch (Exception e) {
                handleDeliveryFailure(outbox, e);
            }
        }
        return pending.size();
    }

    /**
     * 处理投递失败：未超过阈值则递增重试次数，超过则标记失败
     */
    private void handleDeliveryFailure(MqOutbox outbox, Exception e) {
        int currentRetryCount = outbox.getRetryCount() != null ? outbox.getRetryCount() : 0;

        if (currentRetryCount >= MAX_RETRY_COUNT) {
            mqOutboxMapper.markFailed(outbox.getId());
            log.error("Outbox 投递超过最大重试次数({})，已标记失败: outboxId={}, orderNumber={}, retryCount={}",
                    MAX_RETRY_COUNT, outbox.getId(), outbox.getOrderNumber(), currentRetryCount, e);
        } else {
            mqOutboxMapper.incrementRetryCount(outbox.getId());
            log.warn("Outbox 投递失败，将由下轮重试: outboxId={}, orderNumber={}, retryCount={}/{}",
                    outbox.getId(), outbox.getOrderNumber(), currentRetryCount + 1, MAX_RETRY_COUNT);
        }
    }
}

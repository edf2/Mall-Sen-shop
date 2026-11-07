package com.mallsen.task;

import com.mallsen.component.MqOutboxRelayer;
import com.mallsen.component.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MQ Outbox 定时投递任务：每 5 秒拉取待发送消息投递到 RabbitMQ。
 * <p>
 * 配合分布式锁避免多实例重复投递；即使投递失败，消息保留待发送状态下轮重试，
 * 解决支付成功「库已更新、MQ 未发导致销量永不确认」的最终一致性缺口。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MqOutboxRelayTask {

    private static final String LOCK_KEY = "task:mq-outbox-relay";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final int BATCH_SIZE = 100;

    private final RedisLockUtil redisLockUtil;
    private final MqOutboxRelayer mqOutboxRelayer;

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void relayPendingMessages() {
        String token = redisLockUtil.tryLock(LOCK_KEY, LOCK_TTL);
        if (token == null) {
            log.info("MQ Outbox 投递任务已被其他实例持有锁，跳过本轮");
            return;
        }
        try {
            int count = mqOutboxRelayer.relayPending(BATCH_SIZE);
            if (count > 0) {
                log.info("MQ Outbox 本轮投递完成，共 {} 条", count);
            }
        } finally {
            redisLockUtil.unlock(LOCK_KEY, token);
        }
    }
}

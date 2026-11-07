package com.mallsen.task;

import com.mallsen.component.RedisLockUtil;
import com.mallsen.message.AiPredictionMessage;
import com.mallsen.service.impl.StockPredictionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class PredictTask {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final StockPredictionHelper stockPredictionHelper;
    private final RabbitTemplate rabbitTemplate;
    private final RedisLockUtil redisLockUtil;

    private static final int THRESHOLD_DAYS = 7;
    private static final int LOOKBACK_DAYS = 30;

    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyStockPrediction() {
        //分布式锁：多实例部署时仅一个实例执行，防止重复发送 AI 补货推送
        String lockKey = "task:daily-stock-prediction";
        String lockToken = redisLockUtil.tryLock(lockKey, LOCK_TTL);
        if (lockToken == null) {
            log.info("每日库存预测：锁已被其他实例持有，跳过本轮");
            return;
        }
        try {
            log.info("开始执行每日库存预测任务...");

            //与 BusinessAIServiceImpl 共用同一套预测逻辑（批量查询销量，避免 N+1）
            List<Map<String, Object>> warnings = stockPredictionHelper.predict(THRESHOLD_DAYS, LOOKBACK_DAYS);

            if (!warnings.isEmpty()) {
                AiPredictionMessage msg = AiPredictionMessage.builder()
                        .time(LocalDateTime.now())
                        .predictions(warnings)
                        .warningCount(warnings.size())
                        .build();

                rabbitTemplate.convertAndSend("ai.push.exchange", "ai.push", msg);
                log.info("库存预警推送完成，共 {} 个商品需要补货", warnings.size());
            } else {
                log.info("无缺货风险商品");
            }
        } finally {
            redisLockUtil.unlock(lockKey, lockToken);
        }
    }
}

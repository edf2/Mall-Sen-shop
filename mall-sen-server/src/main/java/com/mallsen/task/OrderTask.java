package com.mallsen.task;

import com.mallsen.component.RedisLockUtil;
import com.mallsen.entity.Orders;
import com.mallsen.mapper.OrderMapper;
import com.mallsen.service.impl.StockReleaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    /** 锁 TTL 需大于任务最大执行时长，超时未执行完的订单留待下轮处理 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private StockReleaseService stockReleaseService;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Scheduled(cron = "0 * * * * ?")//每分钟触发一次
    public void processTimeoutOrder() {
        //分布式锁：多实例部署时仅一个实例执行，防止重复取消订单 / 重复归还库存
        String lockKey = "task:process-timeout-order";
        String lockToken = redisLockUtil.tryLock(lockKey, LOCK_TTL);
        if (lockToken == null) {
            log.info("定时处理超时订单：锁已被其他实例持有，跳过本轮");
            return;
        }
        try {
            log.info("定时处理超时订单,{}", LocalDateTime.now());

            LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

            //查询待付款且超过15分钟未支付的订单
            List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);

            if (ordersList != null && ordersList.size() > 0) {
                for (Orders orders : ordersList) {
                    //条件更新：仅当订单仍为「待付款」时才取消，避免与用户正在支付的请求竞态，把刚支付的订单误取消
                    int affected = orderMapper.cancelPendingPaymentOrder(orders.getId(), "支付超时，自动取消", LocalDateTime.now());
                    if (affected > 0) {
                        //仅当本次取消真正生效时才归还预占库存，防止已支付订单被误回库存
                        stockReleaseService.release(orders);
                    }
                }
            }
        } finally {
            redisLockUtil.unlock(lockKey, lockToken);
        }
    }

    @Scheduled(cron = "0 0 1 * * ?")//每天凌晨一点触发一次
    public void processDeliveryOrder() {
        //分布式锁：多实例部署时仅一个实例执行，防止重复完成订单
        String lockKey = "task:process-delivery-order";
        String lockToken = redisLockUtil.tryLock(lockKey, LOCK_TTL);
        if (lockToken == null) {
            log.info("定时处理派送中订单：锁已被其他实例持有，跳过本轮");
            return;
        }
        try {
            log.info("定时处理派送中订单,{}", LocalDateTime.now());

            LocalDateTime time = LocalDateTime.now().plusMinutes(-60);

            List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);

            if (ordersList != null && ordersList.size() > 0) {
                for (Orders orders : ordersList) {
                    //条件更新：仅当订单仍为「派送中」时才完成，避免竞态覆盖
                    orderMapper.completeDeliveryInProgressOrder(orders.getId());
                }
            }
        } finally {
            redisLockUtil.unlock(lockKey, lockToken);
        }
    }

}

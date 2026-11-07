package com.mallsen.component;

import com.mallsen.entity.OrderDetail;
import com.mallsen.entity.Product;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.message.OrderAsyncMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 订单支付成功后的异步处理。
 * 库存已在下单时预占扣减（OrderServiceImpl.submitOrder），这里只确认销量并清理缓存，不再重复扣减库存。
 */
@Component
@Slf4j
public class OrderAsyncConsumer {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ProductCacheEvictor productCacheEvictor;

    @RabbitListener(queues = "order.async.queue")
    public void handleOrderAsync(OrderAsyncMessage msg) {
        if (msg == null || msg.getOrderId() == null) {
            log.warn("异步订单消息缺少订单号，跳过");
            return;
        }
        log.info("异步处理订单，orderId: {}", msg.getOrderId());

        //幂等：以 Redis SETNX 记录已消费订单，防止消息重投（auto-ack 下被退回/重复投递）导致销量重复累加。
        //仅首次成功写入标记的消息才继续，重复消息直接忽略。
        String consumedKey = "order:async:consumed:" + msg.getOrderId();
        Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(consumedKey, "1", Duration.ofDays(7));
        if (!Boolean.TRUE.equals(firstTime)) {
            log.info("异步订单已处理过，忽略重复消息，orderId: {}", msg.getOrderId());
            return;
        }

        try {
            for (OrderDetail detail : msg.getOrderDetailList()) {
                if (detail.getProductId() == null) {
                    continue;
                }

                //确认销量（库存已在下单时预占扣减）
                productMapper.updateProductSales(detail.getProductId(), detail.getNumber());
                redisTemplate.opsForZSet().incrementScore("product:sales", detail.getProductId(), detail.getNumber());

                Product product = productMapper.getById(detail.getProductId());
                if (product != null) {
                    //销量确认后失效分类缓存（MQ 消费无活动事务，立即删除 + 延迟双删兜底）
                    productCacheEvictor.evictCategoryAfterCommit(product.getCategoryId());
                    log.info("订单支付异步处理，已失效商品缓存，分类: {}", product.getCategoryId());
                }
            }

            log.info("异步处理订单完成，orderId: {}", msg.getOrderId());
        } catch (Exception e) {
            log.error("异步处理订单失败，orderId: {}", msg.getOrderId(), e);
            //处理失败：若认为可重试，可在此删除幂等标记后抛出，交由 MQ 重投。
            throw e;
        }
    }
}

package com.mallsen.service.impl;

import com.mallsen.constant.InventoryConstant;
import com.mallsen.component.ProductCacheEvictor;
import com.mallsen.entity.InventoryRecord;
import com.mallsen.entity.OrderDetail;
import com.mallsen.entity.Orders;
import com.mallsen.entity.Product;
import com.mallsen.mapper.InventoryMapper;
import com.mallsen.mapper.OrderDetailMapper;
import com.mallsen.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存归还服务：订单取消/拒单/支付超时等场景统一归还下单时预占的库存。
 * 已支付订单还需回滚销量；未支付订单仅归还库存。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockReleaseService {

    private final OrderDetailMapper orderDetailMapper;
    private final ProductMapper productMapper;
    private final InventoryMapper inventoryMapper;
    private final RedisTemplate redisTemplate;
    private final ProductCacheEvictor productCacheEvictor;

    /**
     * 归还订单预占的库存（含库存流水与缓存清理）
     * @param orderDB 订单（需包含 id、number、userId、payStatus）
     */
    public void release(Orders orderDB) {
        if (orderDB == null || orderDB.getId() == null) {
            return;
        }
        boolean paid = Orders.PAID.equals(orderDB.getPayStatus());
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderDB.getId());
        if (orderDetails == null || orderDetails.isEmpty()) {
            return;
        }

        for (OrderDetail detail : orderDetails) {
            if (detail.getProductId() == null) {
                continue;
            }
            Product product = productMapper.getById(detail.getProductId());
            if (product == null) {
                continue;
            }

            if (paid) {
                productMapper.restoreStockAndSales(detail.getProductId(), detail.getNumber());
                redisTemplate.opsForZSet().incrementScore("product:sales", detail.getProductId(), -detail.getNumber());
            } else {
                productMapper.restoreStock(detail.getProductId(), detail.getNumber());
            }
            //重新读取归还后的实际库存作为流水快照，避免基于 UPDATE 之前的旧值导致账实不符
            Product fresh = productMapper.getById(detail.getProductId());
            int stockAfter = fresh != null ? fresh.getStock() : product.getStock() + detail.getNumber();
            int stockBefore = stockAfter - detail.getNumber();

            InventoryRecord record = InventoryRecord.builder()
                    .productId(detail.getProductId())
                    .type(InventoryConstant.TYPE_INBOUND)
                    .quantity(detail.getNumber())
                    .stockBefore(stockBefore)
                    .stockAfter(stockAfter)
                    .remark("订单取消归还库存：" + orderDB.getNumber())
                    .createTime(LocalDateTime.now())
                    .createUser(orderDB.getUserId())
                    .build();
            inventoryMapper.insertRecord(record);

            //缓存失效：提交后再删（调用方 cancel/reject/refund 均在事务内）+ 延迟双删兜底
            productCacheEvictor.evictCategoryAfterCommit(product.getCategoryId());
            log.info("订单取消，已失效商品缓存，分类: {}", product.getCategoryId());
        }
    }
}

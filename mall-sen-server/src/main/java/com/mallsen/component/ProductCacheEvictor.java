package com.mallsen.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 商品缓存失效器（Cache Aside 模式）。
 * <p>
 * 统一封装「数据变更后如何失效 product_{categoryId} 列表缓存」的策略，避免各调用点
 * 各自手写 delete、策略漂移。策略要点：
 * <ul>
 *   <li><b>提交后再失效</b>：若当前存在事务，缓存删除延迟到 {@code afterCommit} 执行，
 *       避免事务回滚时缓存已被删除造成「库没变、缓存却没了」的空洞；也避免提交前删除
 *       留下脏读窗口。</li>
 *   <li><b>延迟双删</b>：Cache Aside 的经典脏读窗口——读线程在「写库提交后、删缓存前」
 *       把旧库值回写缓存。第一次删除后延迟 1s 再删一次，兜底清掉该窗口期写回的脏数据，
 *       极端并发下仍可能短暂读到旧值，但不会长期残留。</li>
 *   <li>删除失败只记日志不抛异常，缓存最多是多读一次旧值，由下一次变更兜底，不阻断主流程。</li>
 * </ul>
 * 粒度说明：当前 C 端缓存按「分类」聚合为整个商品列表（product_{categoryId}），
 * 因此这里失效的单位是分类。更细粒度需改为按商品维度缓存 + 分类列表由聚合得到，见架构文档。
 */
@Component
@Slf4j
public class ProductCacheEvictor {

    /**
     * 延迟双删的第二次删除间隔：需要大于「读线程回写旧值」的窗口（DB 读 + 序列化 + 写回），
     * 1s 在单机/常规并发下足够；如命中率高可适当调大。
     */
    private static final long DELAYED_DELETE_MS = 1000L;

    /**
     * 延迟双删共享调度器（守护线程，不阻止应用退出）
     */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "product-cache-evictor");
                t.setDaemon(true);
                return t;
            });

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 数据变更后失效分类缓存：有活动事务则提交后执行，无事务立即执行。
     * 两次删除（立即 + 延迟）兜底脏读窗口。
     *
     * @param categoryId 商品分类 ID（为 null 时直接忽略）
     */
    public void evictCategoryAfterCommit(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        Runnable evict = () -> {
            evictCategory(categoryId);
            SCHEDULER.schedule(() -> evictCategory(categoryId), DELAYED_DELETE_MS, TimeUnit.MILLISECONDS);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }

    private void evictCategory(Long categoryId) {
        try {
            redisTemplate.delete("product_" + categoryId);
            log.debug("已删除商品分类缓存，categoryId: {}", categoryId);
        } catch (Exception e) {
            log.error("删除商品分类缓存失败，categoryId: {}", categoryId, e);
        }
    }
}

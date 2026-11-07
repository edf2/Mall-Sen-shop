package com.mallsen.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 Redis SETNX 的分布式锁工具（随机 token + Lua 原子释放）。
 * <p>
 * 用于定时任务等跨实例场景防止重复执行；Redis 不可用时降级放行（返回
 * {@link #LOCK_DEGRADED}），避免依赖故障阻塞业务，释放时对该 token 跳过校验。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisLockUtil {

    /** Redis 不可用降级放行时的特殊 token，释放锁时跳过校验 */
    private static final String LOCK_DEGRADED = "__lock_degraded__";

    private final RedisTemplate redisTemplate;

    /**
     * 尝试获取分布式锁。
     *
     * @param key 锁 key
     * @param ttl 锁持有时间（应大于任务预计执行时长）
     * @return 锁 token；返回 null 表示未获取到锁（他人持有）；返回 {@link #LOCK_DEGRADED}
     *         表示 Redis 不可用已降级放行（仍应执行业务）
     */
    public String tryLock(String key, Duration ttl) {
        try {
            String token = UUID.randomUUID().toString();
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (Exception e) {
            log.error("获取分布式锁失败，key: {}", key, e);
            return LOCK_DEGRADED;
        }
    }

    /**
     * 释放分布式锁：仅当 key 的值仍为本次持有者的 token 时才原子删除（Lua 校验），
     * 避免锁过期被他人重新获取后误删他人锁。
     *
     * @param key   锁 key
     * @param token 获取锁时返回的 token
     */
    public void unlock(String key, String token) {
        if (token == null || LOCK_DEGRADED.equals(token)) {
            return;
        }
        try {
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Collections.singletonList(key), token);
        } catch (Exception e) {
            log.error("释放分布式锁失败，key: {}", key, e);
        }
    }
}

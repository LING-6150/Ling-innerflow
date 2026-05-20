package com.ling.linginnerflow.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 三大缓存防御服务
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  防穿透  (Cache Penetration)                                      │
 * │  问题：查询不存在的 key，每次都穿透打到 DB                            │
 * │  方案：DB 返回 null 时缓存哨兵值 "__NULL__"（TTL=30s），后续命中哨兵   │
 * │        直接返回 null，不再透传 DB                                   │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  防雪崩  (Cache Avalanche)                                        │
 * │  问题：大批 key 同时过期，瞬间涌入 DB                                │
 * │  方案：TTL = baseTtl ± 20% 随机抖动，错开过期时间                    │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  防击穿  (Cache Breakdown / Hotspot)                               │
 * │  问题：热点 key 失效瞬间，大量并发请求同时穿透，重建风暴                 │
 * │  方案：Redis setIfAbsent 互斥锁 + 双重检查                          │
 * │        只有拿到锁的线程重建缓存，其余自旋等待后读已重建的值               │
 * └─────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDefenseService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 空值哨兵：DB 返回 null 时写入此值，防止穿透 */
    static final String NULL_SENTINEL = "__NULL__";

    /** 空值缓存 TTL：短暂缓存，避免正常写入后仍命中旧的 null */
    private static final long NULL_TTL_SECONDS = 30;

    /** 互斥锁 TTL：防止宕机后锁永不释放 */
    private static final long MUTEX_TTL_SECONDS = 5;

    /** 自旋等待间隔 */
    private static final int SPIN_INTERVAL_MS = 50;

    /** 最大自旋次数（50ms × 10 = 最多等 500ms） */
    private static final int MAX_SPIN = 10;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * 防穿透 + 防雪崩
     *
     * 适合低并发、非热点场景（如用户个人记忆读取）。
     * null 结果写哨兵，有效结果 TTL 加抖动。
     *
     * @param key            缓存键
     * @param loader         DB/LLM 回源逻辑，返回 null 表示不存在
     * @param type           反序列化目标类型
     * @param baseTtlMinutes 基础 TTL（分钟），实际 TTL 在 ±20% 内随机
     */
    public <T> T getOrLoad(String key, Supplier<T> loader,
                           Class<T> type, long baseTtlMinutes) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw != null) return decode(raw, type);

            T value = loader.get();
            persist(key, value, baseTtlMinutes);
            return value;

        } catch (Exception e) {
            log.warn("[Cache] getOrLoad degraded, bypassing cache: key={}", key, e);
            return loader.get();
        }
    }

    /**
     * 防穿透 + 防雪崩 + 防击穿
     *
     * 适合热点场景（如高并发内容分析缓存）。
     * 互斥锁保证同一时刻只有一个线程执行回源，双重检查减少锁竞争后的无效加载。
     *
     * @param key            缓存键
     * @param lockKey        互斥锁键（应与 key 关联，如 "lock:" + key）
     * @param loader         回源逻辑
     * @param type           反序列化目标类型
     * @param baseTtlMinutes 基础 TTL（分钟）
     */
    public <T> T getWithMutex(String key, String lockKey, Supplier<T> loader,
                               Class<T> type, long baseTtlMinutes) {
        try {
            // ① 快路径：缓存命中直接返回
            String raw = redisTemplate.opsForValue().get(key);
            if (raw != null) return decode(raw, type);

            // ② 慢路径：竞争互斥锁
            for (int i = 0; i < MAX_SPIN; i++) {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", MUTEX_TTL_SECONDS, TimeUnit.SECONDS);

                if (Boolean.TRUE.equals(acquired)) {
                    try {
                        // ③ 双重检查：获锁后再读一次，可能已由其他线程重建
                        raw = redisTemplate.opsForValue().get(key);
                        if (raw != null) return decode(raw, type);

                        // ④ 回源，写缓存
                        T value = loader.get();
                        persist(key, value, baseTtlMinutes);
                        return value;
                    } finally {
                        redisTemplate.delete(lockKey);
                    }
                }

                // 未抢到锁：等待后看是否已有其他线程写入
                Thread.sleep(SPIN_INTERVAL_MS);
                raw = redisTemplate.opsForValue().get(key);
                if (raw != null) return decode(raw, type);
            }

            // ⑤ 自旋超时降级：直接回源，不写缓存
            log.warn("[Cache] Mutex spin timeout, bypassing cache: key={}", key);
            return loader.get();

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[Cache] Interrupted while waiting for mutex: key={}", key);
            return loader.get();
        } catch (Exception e) {
            log.error("[Cache] getWithMutex failed, bypassing cache: key={}", key, e);
            return loader.get();
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * 防雪崩核心：在基础 TTL 上加 ±20% 随机抖动。
     * Package-private 便于单元测试直接调用。
     */
    long jitter(long baseTtlMinutes) {
        long range = Math.max(1L, baseTtlMinutes / 5);   // 20% of base
        return baseTtlMinutes + ThreadLocalRandom.current().nextLong(-range, range + 1);
    }

    @SuppressWarnings("unchecked")
    private <T> T decode(String raw, Class<T> type) throws Exception {
        if (NULL_SENTINEL.equals(raw)) return null;
        if (type == String.class) return (T) raw;
        return objectMapper.readValue(raw, type);
    }

    private void persist(String key, Object value, long baseTtlMinutes) throws Exception {
        if (value == null) {
            // 防穿透：缓存空值哨兵，短 TTL 避免正常数据写入后遗留
            redisTemplate.opsForValue().set(
                    key, NULL_SENTINEL, NULL_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("[Cache] Null sentinel cached: key={}", key);
        } else {
            String json = (value instanceof String s) ? s
                    : objectMapper.writeValueAsString(value);
            long ttl = jitter(baseTtlMinutes);
            // 防雪崩：使用抖动后的 TTL
            redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.MINUTES);
            log.debug("[Cache] Value cached: key={}, ttl={}min", key, ttl);
        }
    }
}

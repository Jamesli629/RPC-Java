package com.lbc.server.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 幂等检查器
 *
 * 基于 ConcurrentHashMap 实现，存储近期请求的幂等键和响应结果。
 * 支持 TTL 过期清理，防止重复请求（重试场景）。
 *
 * 工作流程：
 * 1. 客户端发送请求时携带 idempotencyKey（UUID）
 * 2. 服务端收到请求，检查幂等键是否已存在
 * 3. 若存在，直接返回缓存的响应（去重）
 * 4. 若不存在，执行方法并将结果缓存
 */
public class IdempotencyChecker {
    private static final Logger logger = LoggerFactory.getLogger(IdempotencyChecker.class);

    /** 幂等键 -> 缓存的响应 */
    private final ConcurrentMap<String, CacheEntry> cache;

    /** TTL（毫秒） */
    private final long ttlMs;

    /** 定时清理器 */
    private final ScheduledExecutorService cleaner;

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        final Object response;
        final long expireTime;

        CacheEntry(Object response, long ttlMs) {
            this.response = response;
            this.expireTime = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    public IdempotencyChecker(long ttlMinutes) {
        this.ttlMs = TimeUnit.MINUTES.toMillis(ttlMinutes);
        this.cache = new ConcurrentHashMap<>();
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "idempotency-cleaner");
            t.setDaemon(true);
            return t;
        });
        // 每分钟清理一次过期条目
        this.cleaner.scheduleAtFixedRate(this::evictExpired, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 检查并记录幂等键
     *
     * @param key      幂等键
     * @param response 响应结果（首次调用时传入）
     * @return 如果 key 已存在，返回缓存的响应；否则返回 null（表示首次调用）
     */
    public Object checkAndRecord(String key, Object response) {
        if (key == null || key.isEmpty()) {
            return null; // 无幂等键，不做检查
        }

        CacheEntry existing = cache.get(key);
        if (existing != null) {
            if (!existing.isExpired()) {
                logger.info("幂等命中，返回缓存结果，key={}", key);
                return existing.response;
            }
            // 过期了，移除
            cache.remove(key);
        }

        // 首次调用，记录响应
        if (response != null) {
            cache.put(key, new CacheEntry(response, ttlMs));
        }
        return null;
    }

    /**
     * 仅检查幂等键是否存在（不记录）
     *
     * @param key 幂等键
     * @return true 表示已存在（重复请求）
     */
    public boolean isDuplicate(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        CacheEntry entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * 获取缓存大小
     */
    public int size() {
        return cache.size();
    }

    /**
     * 清理过期条目
     */
    private void evictExpired() {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - cache.size();
        if (removed > 0) {
            logger.debug("幂等缓存清理：移除 {} 个过期条目，剩余 {}", removed, cache.size());
        }
    }

    /**
     * 停止清理器
     */
    public void stop() {
        cleaner.shutdown();
    }
}

package com.lbc.server.rateLimit;

import com.lbc.server.rateLimit.impl.TokenBucketRateLimitImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 令牌桶限流测试
 */
public class TokenBucketRateLimitTest {

    @Test
    public void testInitialCapacity() {
        // 容量为 5，每 100ms 产生 1 个令牌
        TokenBucketRateLimitImpl limiter = new TokenBucketRateLimitImpl(100, 5);

        // 初始应该能获取 5 个令牌
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.getToken(), "第 " + (i + 1) + " 次应该获取成功");
        }

        // 第 6 个应该失败（令牌已耗尽）
        assertFalse(limiter.getToken());
    }

    @Test
    public void testTokenRefill() throws InterruptedException {
        // 容量为 2，每 200ms 产生 1 个令牌
        TokenBucketRateLimitImpl limiter = new TokenBucketRateLimitImpl(200, 2);

        // 耗尽令牌
        assertTrue(limiter.getToken());
        assertTrue(limiter.getToken());
        assertFalse(limiter.getToken());

        // 等待令牌恢复
        Thread.sleep(250);
        assertTrue(limiter.getToken(), "等待后应该能获取新令牌");
    }

    @Test
    public void testCapacityLimit() throws InterruptedException {
        // 容量为 3，每 100ms 产生 1 个令牌
        TokenBucketRateLimitImpl limiter = new TokenBucketRateLimitImpl(100, 3);

        // 等待一段时间让令牌恢复
        Thread.sleep(350);

        // 获取令牌，应该能获取到容量 + 恢复的令牌数
        int count = 0;
        while (limiter.getToken()) {
            count++;
        }
        // 至少能获取到容量上限的令牌
        assertTrue(count >= 3, "至少应获取到容量上限的令牌: " + count);
        // 不会无限获取（令牌会耗尽）
        assertTrue(count <= 10, "获取令牌数不应无限增长: " + count);
    }
}

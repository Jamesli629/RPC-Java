package com.lbc.client.circuitBreaker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断器状态转换测试
 */
public class CircuitBreakerTest {

    @Test
    public void testInitialState() {
        CircuitBreaker cb = new CircuitBreaker(3, 0.5, 10000, 10, 3);
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
    }

    @Test
    public void testClosedToOpen() {
        CircuitBreaker cb = new CircuitBreaker(3, 0.5, 10000, 10, 3);
        // 3 次失败，触发熔断
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerState.OPEN, cb.getState());
        assertFalse(cb.allowRequest());
    }

    @Test
    public void testOpenToHalfOpen() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(2, 0.5, 100, 10, 3);
        // 触发熔断
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerState.OPEN, cb.getState());

        // 等待恢复时间
        Thread.sleep(150);
        // 超过恢复时间，应该允许请求（进入半开）
        assertTrue(cb.allowRequest());
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());
    }

    @Test
    public void testHalfOpenToClosed() {
        CircuitBreaker cb = new CircuitBreaker(2, 0.5, 0, 10, 5);
        // 强制进入 OPEN 状态
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerState.OPEN, cb.getState());
        // 触发 OPEN -> HALF_OPEN（retryTimePeriod=0，立即恢复）
        assertTrue(cb.allowRequest());
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());

        // 成功率达到阈值，回到 CLOSED
        cb.recordSuccess();
        cb.recordSuccess();
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
    }

    @Test
    public void testHalfOpenToOpen() {
        CircuitBreaker cb = new CircuitBreaker(2, 0.5, 0, 10, 5);
        // 进入 OPEN 状态
        cb.recordFailure();
        cb.recordFailure();
        // 触发 OPEN -> HALF_OPEN
        cb.allowRequest();
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());

        // 半开状态失败，回到 OPEN
        cb.recordFailure();
        assertEquals(CircuitBreakerState.OPEN, cb.getState());
    }

    @Test
    public void testHalfOpenMaxRequests() {
        CircuitBreaker cb = new CircuitBreaker(2, 0.5, 0, 10, 3);
        // 进入 OPEN 状态
        cb.recordFailure();
        cb.recordFailure();
        // 触发 OPEN -> HALF_OPEN（halfOpenRequests 设为 1）
        assertTrue(cb.allowRequest());
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());

        // 半开状态最多允许 halfOpenMaxRequests 个请求（已用 1 个，还能用 2 个）
        assertTrue(cb.allowRequest()); // 第 2 个请求
        assertTrue(cb.allowRequest()); // 第 3 个请求
        assertFalse(cb.allowRequest()); // 第 4 个被限流（超过 halfOpenMaxRequests=3）
    }

    @Test
    public void testFailureRate() {
        CircuitBreaker cb = new CircuitBreaker(5, 0.5, 10000, 10, 3);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess();
        // 3 次调用中 2 次失败
        assertEquals(2.0 / 3, cb.getFailureRate(), 0.01);
    }
}

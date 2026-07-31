package com.lbc.client.circuitBreaker;

import com.lbc.common.metrics.RpcMetrics;
import lombok.Getter;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 三态熔断器（增强版）
 *
 * 增强功能：
 * 1. 滑动窗口统计：记录最近 N 次调用的成功/失败，替代简单计数器
 * 2. 半开状态限流：HALF_OPEN 状态下只允许少量探测请求通过
 * 3. Metrics 集成：状态变化时记录指标
 *
 * 状态转换：
 * CLOSED -> OPEN：滑动窗口内失败率超过阈值
 * OPEN -> HALF_OPEN：超过恢复时间
 * HALF_OPEN -> CLOSED：探测成功率达到阈值
 * HALF_OPEN -> OPEN：探测失败
 */
public class CircuitBreaker {
    //当前状态
    @Getter
    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;

    // 滑动窗口：记录每次调用的结果（true=成功, false=失败）
    private final Queue<Boolean> slidingWindow;
    private final int windowSize;

    // 半开状态限流
    private final AtomicInteger halfOpenRequests = new AtomicInteger(0);
    private final int halfOpenMaxRequests;

    //失败次数阈值（窗口内失败数 >= 此值则熔断）
    private final int failureThreshold;
    //半开启->关闭状态的成功率
    private final double halfOpenSuccessRate;
    //恢复时间
    private final long retryTimePeriod;
    //上一次失败时间
    private volatile long lastFailureTime = 0;

    // 成功/失败计数（用于半开状态判断）
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);

    /**
     * @param failureThreshold    滑动窗口内失败次数阈值
     * @param halfOpenSuccessRate 半开状态成功率阈值
     * @param retryTimePeriod     恢复时间（毫秒）
     * @param windowSize          滑动窗口大小
     * @param halfOpenMaxRequests 半开状态最大探测请求数
     */
    public CircuitBreaker(int failureThreshold, double halfOpenSuccessRate, long retryTimePeriod,
                          int windowSize, int halfOpenMaxRequests) {
        this.failureThreshold = failureThreshold;
        this.halfOpenSuccessRate = halfOpenSuccessRate;
        this.retryTimePeriod = retryTimePeriod;
        this.windowSize = windowSize;
        this.halfOpenMaxRequests = halfOpenMaxRequests;
        this.slidingWindow = new LinkedList<>();
    }

    //查看当前熔断器是否允许请求通过
    public synchronized boolean allowRequest() {
        long currentTime = System.currentTimeMillis();
        switch (state) {
            case OPEN:
                if (currentTime - lastFailureTime >= retryTimePeriod) {
                    state = CircuitBreakerState.HALF_OPEN;
                    halfOpenRequests.set(1); // 当前这个请求算第 1 个探测请求
                    halfOpenSuccessCount.set(0);
                    recordMetrics();
                    return true;
                }
                return false;
            case HALF_OPEN:
                // 半开状态限流：只允许少量探测请求
                int current = halfOpenRequests.incrementAndGet();
                return current <= halfOpenMaxRequests;
            case CLOSED:
            default:
                return true;
        }
    }

    //记录成功
    public synchronized void recordSuccess() {
        if (state == CircuitBreakerState.HALF_OPEN) {
            int success = halfOpenSuccessCount.incrementAndGet();
            int total = halfOpenRequests.get();
            if (total > 0 && (double) success / total >= halfOpenSuccessRate) {
                state = CircuitBreakerState.CLOSED;
                clearWindow();
                recordMetrics();
            }
        }
        addWindowResult(true);
    }

    //记录失败
    public synchronized void recordFailure() {
        lastFailureTime = System.currentTimeMillis();
        addWindowResult(false);

        if (state == CircuitBreakerState.HALF_OPEN) {
            state = CircuitBreakerState.OPEN;
            recordMetrics();
        } else if (state == CircuitBreakerState.CLOSED) {
            // 检查滑动窗口内失败数是否达到阈值
            if (getWindowFailureCount() >= failureThreshold) {
                state = CircuitBreakerState.OPEN;
                recordMetrics();
            }
        }
    }

    /**
     * 添加滑动窗口结果
     */
    private void addWindowResult(boolean success) {
        slidingWindow.offer(success);
        if (slidingWindow.size() > windowSize) {
            slidingWindow.poll();
        }
    }

    /**
     * 获取滑动窗口内失败次数
     */
    private int getWindowFailureCount() {
        int count = 0;
        for (Boolean result : slidingWindow) {
            if (!result) {
                count++;
            }
        }
        return count;
    }

    /**
     * 清空滑动窗口
     */
    private void clearWindow() {
        slidingWindow.clear();
    }

    /**
     * 记录熔断器状态指标
     */
    private void recordMetrics() {
        RpcMetrics.recordCircuitBreakerState("default", state.name());
    }

    /**
     * 获取当前滑动窗口内失败率（用于监控）
     */
    public double getFailureRate() {
        if (slidingWindow.isEmpty()) {
            return 0.0;
        }
        return (double) getWindowFailureCount() / slidingWindow.size();
    }
}

enum CircuitBreakerState {
    // 开启、关闭、半开启
    OPEN, CLOSED, HALF_OPEN
}

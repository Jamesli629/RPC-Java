package com.lbc.client.circuitBreaker;

import com.lbc.common.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断器提供者，参数从配置读取
 *
 * 增强：支持滑动窗口大小、半开状态最大请求数配置
 */
public class CircuitBreakerProvider {

    private final ConfigManager config = ConfigManager.getInstance();

    private Map<String, CircuitBreaker> circuitBreakerMap = new HashMap<>();

    public CircuitBreaker getCircuitBreaker(String serviceName) {
        CircuitBreaker circuitBreaker;
        if (circuitBreakerMap.containsKey(serviceName)) {
            circuitBreaker = circuitBreakerMap.get(serviceName);
        } else {
            // 从配置读取熔断器参数
            int failureThreshold = config.getInt("rpc.client.circuit-breaker.failure-threshold", 3);
            double halfOpenSuccessRate = config.getDouble("rpc.client.circuit-breaker.half-open-success-rate", 0.5);
            long retryTimePeriod = config.getLong("rpc.client.circuit-breaker.retry-time-period-ms", 10000);
            int windowSize = config.getInt("rpc.client.circuit-breaker.sliding-window-size", 10);
            int halfOpenMaxRequests = config.getInt("rpc.client.circuit-breaker.half-open-max-requests", 3);

            circuitBreaker = new CircuitBreaker(failureThreshold, halfOpenSuccessRate, retryTimePeriod,
                    windowSize, halfOpenMaxRequests);
            circuitBreakerMap.put(serviceName, circuitBreaker);
        }
        return circuitBreaker;
    }
}

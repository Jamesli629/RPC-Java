package com.lbc.client.circuitBreaker;

import com.lbc.common.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Lbc
 * @date 2024/10/14 14:38
 *
 * 熔断器提供者，参数从配置读取
 **/
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

            circuitBreaker = new CircuitBreaker(failureThreshold, halfOpenSuccessRate, retryTimePeriod);
            circuitBreakerMap.put(serviceName, circuitBreaker);
        }
        return circuitBreaker;
    }
}

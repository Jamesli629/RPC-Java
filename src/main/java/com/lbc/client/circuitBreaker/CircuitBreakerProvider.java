package com.lbc.client.circuitBreaker;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Lbc
 * @date 2024/10/14 14:38
 **/
public class CircuitBreakerProvider {
    private Map<String, CircuitBreaker> circuitBreakerMap = new HashMap<>();

    public CircuitBreaker getCircuitBreaker(String serviceName) {
        CircuitBreaker circuitBreaker;
        if (circuitBreakerMap.containsKey(serviceName)) {
            circuitBreaker = circuitBreakerMap.get(serviceName);
        } else {
            circuitBreaker = new CircuitBreaker(3, 0.5, 10000);
            circuitBreakerMap.put(serviceName, circuitBreaker);
        }
        return circuitBreaker;
    }
}

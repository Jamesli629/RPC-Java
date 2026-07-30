package com.lbc.server.rateLimit.provider;

import com.lbc.server.rateLimit.RateLimit;
import com.lbc.server.rateLimit.impl.TokenBucketRateLimitImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Lbc
 * @date 2024/10/11 22:41
 **/
public class RateLimitProvider {

    private Map<String, RateLimit> rateLimitMap = new HashMap<>();

    public RateLimit getRateLimit(String interfaceName) {
        if (!rateLimitMap.containsKey(interfaceName)) {
            RateLimit rateLimit = new TokenBucketRateLimitImpl(100, 10);
            rateLimitMap.put(interfaceName, rateLimit);
            return rateLimit;
        }
        return rateLimitMap.get(interfaceName);
    }

}

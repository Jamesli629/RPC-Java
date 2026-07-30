package com.lbc.server.rateLimit.provider;

import com.lbc.common.config.ConfigManager;
import com.lbc.server.rateLimit.RateLimit;
import com.lbc.server.rateLimit.impl.TokenBucketRateLimitImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Lbc
 * @date 2024/10/11 22:41
 *
 * 限流器提供者，参数从配置读取
 **/
public class RateLimitProvider {

    private final ConfigManager config = ConfigManager.getInstance();

    private Map<String, RateLimit> rateLimitMap = new HashMap<>();

    public RateLimit getRateLimit(String interfaceName) {
        if (!rateLimitMap.containsKey(interfaceName)) {
            // 从配置读取限流参数
            int rateMs = config.getInt("rpc.server.rate-limit.rate-ms", 100);
            int capacity = config.getInt("rpc.server.rate-limit.capacity", 10);

            RateLimit rateLimit = new TokenBucketRateLimitImpl(rateMs, capacity);
            rateLimitMap.put(interfaceName, rateLimit);
            return rateLimit;
        }
        return rateLimitMap.get(interfaceName);
    }

}

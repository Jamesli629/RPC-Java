package com.lbc.client.retry;

import com.github.rholder.retry.*;
import com.lbc.client.rpcClient.RpcClient;
import com.lbc.common.config.ConfigManager;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Lbc
 * @date 2024/09/26 09:51
 *
 * 基于 Guava Retrying 的重试机制，参数从配置读取
 **/
public class GuavaRetry {

    private static final Logger logger = LoggerFactory.getLogger(GuavaRetry.class);

    private final ConfigManager config = ConfigManager.getInstance();

    private RpcClient rpcClient;

    public RpcResponse sendServiceWithRetry(RpcRequest request, RpcClient rpcClient) {
        this.rpcClient = rpcClient;

        // 从配置读取重试参数
        long waitSeconds = config.getLong("rpc.client.retry.wait-seconds", 2);
        int maxAttempts = config.getInt("rpc.client.retry.max-attempts", 3);
        final int triggerCode = config.getInt("rpc.client.retry.trigger-code", 500);

        Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
                //无论出现什么异常，都进行重试
                .retryIfException()
                //返回结果为 error时进行重试
                .retryIfResult(response -> Objects.equals(response.getCode(), triggerCode))
                //重试等待策略
                .withWaitStrategy(WaitStrategies.fixedWait(waitSeconds, TimeUnit.SECONDS))
                //重试停止策略
                .withStopStrategy(StopStrategies.stopAfterAttempt(maxAttempts))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        logger.info("RetryListener: 第{}次调用", attempt.getAttemptNumber());
                    }
                })
                .build();
        try {
            return retryer.call(() -> rpcClient.sendRequest(request));
        } catch (Exception e) {
            logger.error("重试{}次后仍然失败", maxAttempts, e);
        }
        return RpcResponse.fail();
    }

}

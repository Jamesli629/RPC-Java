package com.lbc.client.cluster.impl;

import com.lbc.client.cluster.Cluster;
import com.lbc.client.rpcClient.RpcClient;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 并行调用策略
 *
 * 并行调用多个服务节点，取第一个成功的响应。适用于实时性要求高的读操作。
 */
public class ForkingCluster implements Cluster {
    private static final Logger logger = LoggerFactory.getLogger(ForkingCluster.class);

    private final RpcClient rpcClient;
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "forking-cluster");
        t.setDaemon(true);
        return t;
    });

    public ForkingCluster(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public RpcResponse invoke(RpcRequest request, List<String> addressList) throws Exception {
        if (addressList == null || addressList.isEmpty()) {
            throw new RuntimeException("没有可用的服务地址");
        }

        // 并行调用所有节点
        CompletableFuture<RpcResponse>[] futures = new CompletableFuture[addressList.size()];
        for (int i = 0; i < addressList.size(); i++) {
            final int index = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    return rpcClient.sendRequest(request);
                } catch (Exception e) {
                    logger.warn("Forking 第 {} 个节点调用失败: {}", index, e.getMessage());
                    return null;
                }
            }, executor);
        }

        // 等待第一个成功响应（最多等 5 秒）
        try {
            for (CompletableFuture<RpcResponse> future : futures) {
                RpcResponse response = future.get(5, TimeUnit.SECONDS);
                if (response != null && response.getCode() == 200) {
                    // 取消其他任务
                    for (CompletableFuture<RpcResponse> f : futures) {
                        f.cancel(true);
                    }
                    return response;
                }
            }
        } catch (TimeoutException e) {
            logger.warn("Forking 策略超时");
        }

        throw new RuntimeException("Forking 策略：所有节点调用失败");
    }

    @Override
    public String name() {
        return "forking";
    }
}

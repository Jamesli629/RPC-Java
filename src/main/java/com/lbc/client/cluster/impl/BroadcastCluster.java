package com.lbc.client.cluster.impl;

import com.lbc.client.cluster.Cluster;
import com.lbc.client.rpcClient.RpcClient;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 广播调用策略
 *
 * 调用所有服务节点，只要有一个成功即算成功。适用于刷新缓存、推送配置等场景。
 */
public class BroadcastCluster implements Cluster {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastCluster.class);

    private final RpcClient rpcClient;
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "broadcast-cluster");
        t.setDaemon(true);
        return t;
    });

    public BroadcastCluster(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public RpcResponse invoke(RpcRequest request, List<String> addressList) throws Exception {
        if (addressList == null || addressList.isEmpty()) {
            throw new RuntimeException("没有可用的服务地址");
        }

        // 广播调用所有节点
        List<CompletableFuture<RpcResponse>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < addressList.size(); i++) {
            final int index = i;
            CompletableFuture<RpcResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    RpcResponse response = rpcClient.sendRequest(request);
                    if (response != null && response.getCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                    return response;
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    logger.warn("Broadcast 第 {} 个节点调用失败: {}", index, e.getMessage());
                    return null;
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有调用完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        logger.info("Broadcast 完成: 总节点={}, 成功={}, 失败={}",
                addressList.size(), successCount.get(), failCount.get());

        // 只要有一个成功即返回成功
        if (successCount.get() > 0) {
            return RpcResponse.sussess(null);
        }
        throw new RuntimeException("Broadcast 策略：所有节点调用失败");
    }

    @Override
    public String name() {
        return "broadcast";
    }
}

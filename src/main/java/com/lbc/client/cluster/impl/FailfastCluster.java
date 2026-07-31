package com.lbc.client.cluster.impl;

import com.lbc.client.cluster.Cluster;
import com.lbc.client.rpcClient.RpcClient;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;

import java.util.List;

/**
 * 快速失败策略（默认）
 *
 * 只调用一次，失败立即抛出异常。适用于幂等性要求高的写操作。
 */
public class FailfastCluster implements Cluster {
    private final RpcClient rpcClient;

    public FailfastCluster(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public RpcResponse invoke(RpcRequest request, List<String> addressList) throws Exception {
        // 直接调用一次，失败即抛出
        return rpcClient.sendRequest(request);
    }

    @Override
    public String name() {
        return "failfast";
    }
}

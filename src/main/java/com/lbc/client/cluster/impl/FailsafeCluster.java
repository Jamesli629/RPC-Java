package com.lbc.client.cluster.impl;

import com.lbc.client.cluster.Cluster;
import com.lbc.client.rpcClient.RpcClient;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 失败安全策略
 *
 * 调用失败返回 null，不抛异常。适用于非核心调用（如日志上报、监控数据）。
 */
public class FailsafeCluster implements Cluster {
    private static final Logger logger = LoggerFactory.getLogger(FailsafeCluster.class);

    private final RpcClient rpcClient;

    public FailsafeCluster(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public RpcResponse invoke(RpcRequest request, List<String> addressList) throws Exception {
        try {
            return rpcClient.sendRequest(request);
        } catch (Exception e) {
            logger.warn("Failsafe 策略：调用失败返回 null，接口: {}.{}",
                    request.getInterfaceName(), request.getMethodName());
            return null;
        }
    }

    @Override
    public String name() {
        return "failsafe";
    }
}

package com.lbc.client.cluster;

import com.lbc.client.cluster.impl.*;
import com.lbc.client.rpcClient.RpcClient;
import com.lbc.common.config.ConfigManager;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 集群调用入口
 *
 * 根据配置的容错策略，调用对应的 Cluster 实现。
 * 策略可通过配置切换：rpc.client.cluster.strategy=failfast/failsafe/forking/broadcast
 */
public class ClusterInvoker {
    private static final Logger logger = LoggerFactory.getLogger(ClusterInvoker.class);

    private final RpcClient rpcClient;
    private Cluster cluster;
    private static final Map<String, Cluster> CLUSTER_CACHE = new HashMap<>();

    public ClusterInvoker(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
        initCluster();
    }

    /**
     * 根据配置初始化集群策略
     */
    private void initCluster() {
        String strategy = ConfigManager.getInstance()
                .getString("rpc.client.cluster.strategy", "failfast");

        synchronized (CLUSTER_CACHE) {
            cluster = CLUSTER_CACHE.get(strategy);
            if (cluster == null) {
                switch (strategy.toLowerCase()) {
                    case "failsafe":
                        cluster = new FailsafeCluster(rpcClient);
                        break;
                    case "forking":
                        cluster = new ForkingCluster(rpcClient);
                        break;
                    case "broadcast":
                        cluster = new BroadcastCluster(rpcClient);
                        break;
                    case "failfast":
                    default:
                        cluster = new FailfastCluster(rpcClient);
                        break;
                }
                CLUSTER_CACHE.put(strategy, cluster);
            }
        }
        logger.info("集群容错策略初始化: {}", cluster.name());
    }

    /**
     * 执行集群调用
     *
     * @param request     RPC 请求
     * @param addressList 可用服务地址列表
     * @return RPC 响应
     */
    public RpcResponse invoke(RpcRequest request, List<String> addressList) throws Exception {
        return cluster.invoke(request, addressList);
    }

    /**
     * 获取当前策略名称
     */
    public String getStrategyName() {
        return cluster.name();
    }
}

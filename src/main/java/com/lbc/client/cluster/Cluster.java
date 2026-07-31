package com.lbc.client.cluster;

import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;

import java.util.List;

/**
 * 集群容错策略接口
 *
 * 定义客户端调用服务端集群时的容错行为：
 * - Failfast：快速失败（默认）
 * - Failsafe：失败安全（返回 null）
 * - Forking：并行调用多个节点，取第一个成功
 * - Broadcast：广播调用所有节点
 */
public interface Cluster {
    /**
     * 执行集群调用
     *
     * @param request       RPC 请求
     * @param addressList   可用服务地址列表
     * @return RPC 响应
     */
    RpcResponse invoke(RpcRequest request, List<String> addressList) throws Exception;

    /**
     * 策略名称
     */
    String name();
}

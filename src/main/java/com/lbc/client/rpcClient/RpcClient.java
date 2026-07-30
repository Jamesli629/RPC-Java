package com.lbc.client.rpcClient;

import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;

import java.util.concurrent.CompletableFuture;

public interface RpcClient {
    /**
     * 同步发送请求（阻塞等待响应）
     */
    RpcResponse sendRequest(RpcRequest request);

    /**
     * 异步发送请求（立即返回 Future，不阻塞调用线程）
     */
    CompletableFuture<RpcResponse> sendRequestAsync(RpcRequest request);
}

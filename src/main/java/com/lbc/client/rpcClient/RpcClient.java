package com.lbc.client.rpcClient;

import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;

public interface RpcClient {
    //定义底层通信的方法
    RpcResponse sendRequest(RpcRequest request);
}

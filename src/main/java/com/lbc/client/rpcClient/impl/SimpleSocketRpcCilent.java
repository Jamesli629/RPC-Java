package com.lbc.client.rpcClient.impl;

import com.lbc.client.rpcClient.RpcClient;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

/**
 * @author Lbc
 * @date 2024/09/16 15:38
 **/
public class SimpleSocketRpcCilent implements RpcClient {
    private String host;
    private int port;

    public SimpleSocketRpcCilent(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        try {
            Socket socket = new Socket(host, port);
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            oos.writeObject(request);
            oos.flush();

            RpcResponse response = (RpcResponse) ois.readObject();
            return response;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public CompletableFuture<RpcResponse> sendRequestAsync(RpcRequest request) {
        // 简单实现：同步调用包装为 CompletableFuture
        return CompletableFuture.completedFuture(sendRequest(request));
    }
}

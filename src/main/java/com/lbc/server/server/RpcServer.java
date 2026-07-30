package com.lbc.server.server;

public interface RpcServer {
    //开启监听
    void start(int port);

    void stop();
}

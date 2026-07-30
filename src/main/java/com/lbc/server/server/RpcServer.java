package com.lbc.server.server;

public interface RpcServer {
    //开启监听
    void start(int port);

    //停止服务端
    void stop();

    //判断服务端是否正在运行
    boolean isRunning();
}

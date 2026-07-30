package com.lbc.server.serviceRegister;

import java.net.InetSocketAddress;

// 服务注册接口
public interface ServiceRegister {
    //  注册：保存服务与地址。
    void register(String serviceName, InetSocketAddress serviceAddress, boolean canTry);

    // 注销：删除服务地址
    void unregister(String serviceName, InetSocketAddress serviceAddress);
}

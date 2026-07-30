package com.lbc.server;

import com.lbc.common.config.ConfigManager;
import com.lbc.common.service.Impl.UserServiceImpl;
import com.lbc.common.service.UserService;
import com.lbc.server.provider.ServiceProvider;
import com.lbc.server.server.RpcServer;
import com.lbc.server.server.impl.NettyRPCRPCServer;


public class TestServer {
    public static void main(String[] args) {
        // 从配置读取服务地址和端口
        ConfigManager config = ConfigManager.getInstance();
        String host = config.getString("rpc.server.host", "127.0.0.1");
        int port = config.getInt("rpc.server.port", 9999);

        UserService userService = new UserServiceImpl();

        ServiceProvider serviceProvider = new ServiceProvider(host, port);
        serviceProvider.provideServiceInterface(userService, true);

        RpcServer rpcServer = new NettyRPCRPCServer(serviceProvider);
        rpcServer.start(port);
    }
}

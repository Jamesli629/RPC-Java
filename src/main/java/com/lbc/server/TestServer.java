package com.lbc.server;

import com.lbc.common.service.Impl.UserServiceImpl;
import com.lbc.common.service.UserService;
import com.lbc.server.provider.ServiceProvider;
import com.lbc.server.server.RpcServer;
import com.lbc.server.server.impl.NettyRPCRPCServer;
import com.lbc.server.server.impl.SimpleRPCRPCServer;
import com.lbc.server.server.impl.ThreadPoolRPCRPCServer;


public class TestServer {
    public static void main(String[] args) {
        UserService userService = new UserServiceImpl();

        ServiceProvider serviceProvider = new ServiceProvider("127.0.0.1", 9999);
        serviceProvider.provideServiceInterface(userService, true);

        RpcServer rpcServer = new NettyRPCRPCServer(serviceProvider);
        rpcServer.start(9999);
    }
}

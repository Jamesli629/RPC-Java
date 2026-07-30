package com.lbc.server.server.impl;

import com.lbc.common.config.ConfigManager;
import com.lbc.server.provider.ServiceProvider;
import com.lbc.server.server.RpcServer;
import com.lbc.server.server.work.WorkThread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class ThreadPoolRPCRPCServer implements RpcServer {
    private final ThreadPoolExecutor threadPool;
    private ServiceProvider serviceProvider;

    public ThreadPoolRPCRPCServer(ServiceProvider serviceProvider) {
        // 从配置读取线程池参数
        ConfigManager config = ConfigManager.getInstance();
        int maxSize = config.getInt("rpc.server.thread-pool.max-size", 1000);
        long keepAliveSeconds = config.getLong("rpc.server.thread-pool.keep-alive-seconds", 60);
        int queueCapacity = config.getInt("rpc.server.thread-pool.queue-capacity", 100);

        threadPool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                maxSize, keepAliveSeconds, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueCapacity));
        this.serviceProvider = serviceProvider;
    }

    public ThreadPoolRPCRPCServer(ServiceProvider serviceProvider, int corePoolSize,
                                  int maximumPoolSize,
                                  long keepAliveTime,
                                  TimeUnit unit,
                                  BlockingQueue<Runnable> workQueue) {

        threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void start(int port) {
        System.out.println("服务端启动了");
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            while (true) {
                Socket socket = serverSocket.accept();
                threadPool.execute(new WorkThread(socket, serviceProvider));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {

    }
}

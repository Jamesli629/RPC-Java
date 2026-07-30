package com.lbc.server.server.impl;

import com.lbc.server.netty.nettyInitializer.NettyServerInitializer;
import com.lbc.server.provider.ServiceProvider;
import com.lbc.server.server.RpcServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lbc
 * @date 2024/09/16 15:50
 *
 * 支持优雅下线的 Netty RPC 服务端
 **/
public class NettyRPCRPCServer implements RpcServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyRPCRPCServer.class);

    // 优雅下线超时时间（毫秒）
    private static final long SHUTDOWN_TIMEOUT_MS = 30_000;

    private ServiceProvider serviceProvider;

    // 提升为实例变量，stop() 可引用
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workGroup;
    private ChannelFuture channelFuture;

    // 优雅下线状态
    private volatile boolean running = false;
    // 排空状态（true 时拒绝新请求）
    private volatile boolean draining = false;
    // 在途请求计数
    private final AtomicInteger inFlightRequests = new AtomicInteger(0);

    public NettyRPCRPCServer(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void start(int port) {
        bossGroup = new NioEventLoopGroup();
        workGroup = new NioEventLoopGroup();
        running = true;
        logger.info("netty服务端启动了，端口: {}", port);
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new NettyServerInitializer(serviceProvider, this));
            channelFuture = serverBootstrap.bind(port).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("服务端被中断");
        } finally {
            running = false;
            logger.info("netty服务端已停止");
        }
    }

    @Override
    public void stop() {
        if (!running) {
            logger.warn("服务端未在运行，无需停止");
            return;
        }

        logger.info("开始优雅下线...");
        // ① 进入排空状态，拒绝新请求
        draining = true;

        // ② 从 ZK 注销服务（通知客户端停止路由到此节点）
        try {
            serviceProvider.unregisterAll();
            logger.info("ZK 服务注销完成");
        } catch (Exception e) {
            logger.error("ZK 服务注销失败", e);
        }

        // ③ 等待在途请求完成（设置超时，避免无限等待）
        long deadline = System.currentTimeMillis() + SHUTDOWN_TIMEOUT_MS;
        while (inFlightRequests.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int remaining = inFlightRequests.get();
        if (remaining > 0) {
            logger.warn("优雅下线超时，仍有 {} 个在途请求未完成", remaining);
        } else {
            logger.info("所有在途请求已完成");
        }

        // ④ 关闭 Netty 资源
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workGroup != null) {
            workGroup.shutdownGracefully();
        }
        running = false;
        logger.info("优雅下线完成");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 请求开始时调用，增加在途计数
     */
    public void onRequestStart() {
        inFlightRequests.incrementAndGet();
    }

    /**
     * 请求结束时调用，减少在途计数
     */
    public void onRequestEnd() {
        inFlightRequests.decrementAndGet();
    }

    /**
     * 是否处于排空状态（拒绝新请求）
     */
    public boolean isDraining() {
        return draining;
    }
}

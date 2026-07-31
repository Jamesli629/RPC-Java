package com.lbc.client.rpcClient.impl;

import com.lbc.client.netty.nettyInitializer.NettyClientInitializer;
import com.lbc.client.rpcClient.RpcClient;
import com.lbc.client.serverCenter.ServiceCenter;
import com.lbc.common.config.ConfigManager;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lbc
 * @date 2024/09/16 15:39
 *
 * 基于连接复用的Netty RPC客户端，支持同步和异步两种调用模式。
 * 通过channelId匹配请求与响应，支持同一连接上的并发请求。
 * 按服务名缓存Channel，同一服务的请求复用同一连接。
 **/
public class NettyRpcClient implements RpcClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyRpcClient.class);

    private static final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

    //请求超时时间（秒），从配置读取
    private final int requestTimeout = ConfigManager.getInstance()
            .getInt("rpc.client.request-timeout-seconds", 10);

    private final ServiceCenter serviceCenter;
    //按服务名缓存Channel，同一服务的请求复用同一连接
    private final ConcurrentHashMap<String, Channel> channelCache = new ConcurrentHashMap<>();
    //等待响应的Future映射：channelId -> CompletableFuture
    private final ConcurrentHashMap<Integer, CompletableFuture<RpcResponse>> pendingFutures
            = new ConcurrentHashMap<>();
    //请求ID生成器
    private final AtomicInteger requestIdGenerator = new AtomicInteger(0);
    //超时调度器
    private final ScheduledExecutorService timeoutScheduler =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "rpc-timeout-scheduler");
                t.setDaemon(true);
                return t;
            });

    public NettyRpcClient(ServiceCenter serviceCenter) {
        this.serviceCenter = serviceCenter;
    }

    /**
     * 根据服务名获取或创建Channel。
     * 同一服务名的请求复用同一连接，避免每次请求新建TCP连接。
     */
    private Channel getOrCreateChannel(String serviceName) throws InterruptedException {
        Channel channel = channelCache.get(serviceName);
        if (channel != null && channel.isActive()) {
            return channel;
        }
        //不可用或不存在，同步创建新连接
        synchronized (this) {
            //双重检查
            channel = channelCache.get(serviceName);
            if (channel != null && channel.isActive()) {
                return channel;
            }
            //关闭旧的不活跃通道
            if (channel != null) {
                channel.close();
            }
            InetSocketAddress address = serviceCenter.serviceDiscovery(serviceName);
            if (address == null) {
                throw new RuntimeException("没有可用的服务地址: " + serviceName);
            }
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class)
                    .handler(new NettyClientInitializer(this));
            ChannelFuture channelFuture = bootstrap.connect(address.getHostName(), address.getPort()).sync();
            channel = channelFuture.channel();
            channelCache.put(serviceName, channel);
            return channel;
        }
    }

    /**
     * 同步发送请求（向后兼容）
     * 内部委托给异步方法，通过 join() 阻塞等待结果。
     * 若调用失败（服务不可用、超时等），抛出明确的异常，避免上层得到 null 后产生无意义的 NPE。
     */
    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        try {
            return sendRequestAsync(request).join();
        } catch (Exception e) {
            logger.error("同步发送请求失败，channelId={}", request.getChannelId(), e);
            // 提取根因，抛出明确的异常信息
            Throwable cause = e;
            if (e.getCause() != null) {
                cause = e.getCause();
            }
            throw new RuntimeException("RPC 调用失败: " + cause.getMessage(), cause);
        }
    }

    /**
     * 异步发送请求（立即返回 Future，不阻塞调用线程）
     */
    @Override
    public CompletableFuture<RpcResponse> sendRequestAsync(RpcRequest request) {
        int channelId = requestIdGenerator.incrementAndGet();
        request.setChannelId(channelId);

        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        pendingFutures.put(channelId, future);

        // 设置超时
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            pendingFutures.remove(channelId);
            future.completeExceptionally(
                new TimeoutException("请求超时, channelId=" + channelId));
        }, requestTimeout, TimeUnit.SECONDS);

        try {
            Channel channel = getOrCreateChannel(request.getInterfaceName());
            channel.writeAndFlush(request).addListener(f -> {
                if (!f.isSuccess()) {
                    pendingFutures.remove(channelId);
                    future.completeExceptionally(f.cause());
                }
            });
        } catch (Exception e) {
            pendingFutures.remove(channelId);
            future.completeExceptionally(e);
        }

        // 响应到达或异常时取消超时任务
        return future.whenComplete((resp, err) -> timeoutTask.cancel(false));
    }

    /**
     * NettyClientHandler收到响应时调用，根据channelId找到对应的Future并完成。
     */
    public void handleResponse(RpcResponse response) {
        int channelId = response.getChannelId();
        CompletableFuture<RpcResponse> future = pendingFutures.remove(channelId);
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * Channel 关闭时调用，根据远程地址清理缓存并释放等待中的请求。
     *
     * @param remoteAddress 断开的远程地址字符串
     */
    public void handleChannelInactive(String remoteAddress) {
        // 从 channelCache 中移除所有指向该地址的 Channel
        channelCache.entrySet().removeIf(entry -> {
            Channel ch = entry.getValue();
            if (ch == null || !ch.isActive()) {
                return true;
            }
            String addr = ch.remoteAddress() != null ? ch.remoteAddress().toString() : "";
            return addr.contains(remoteAddress) || addr.equals(remoteAddress);
        });
        logger.info("清理断开的连接: {}", remoteAddress);
        // 释放所有等待中的 Future，避免永久阻塞直到超时
        if (!pendingFutures.isEmpty()) {
            logger.warn("释放 {} 个等待中的请求", pendingFutures.size());
            pendingFutures.forEach((id, future) -> {
                future.completeExceptionally(new RuntimeException("连接已断开: " + remoteAddress));
            });
            pendingFutures.clear();
        }
    }
}

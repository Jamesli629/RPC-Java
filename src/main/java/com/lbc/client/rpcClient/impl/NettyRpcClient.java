package com.lbc.client.rpcClient.impl;

import com.lbc.client.netty.nettyInitializer.NettyClientInitializer;
import com.lbc.client.rpcClient.RpcClient;
import com.lbc.client.serverCenter.ServiceCenter;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Lbc
 * @date 2024/09/16 15:39
 *
 * 基于连接复用的Netty RPC客户端，避免每次请求新建TCP连接。
 * 通过channelId匹配请求与响应，支持同一连接上的并发请求。
 * 按服务名缓存Channel，同一服务的请求复用同一连接。
 **/
public class NettyRpcClient implements RpcClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyRpcClient.class);

    private static final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

    //请求超时时间（秒）
    private static final int REQUEST_TIMEOUT = 10;

    private final ServiceCenter serviceCenter;
    //按服务名缓存Channel，同一服务的请求复用同一连接
    private final ConcurrentHashMap<String, Channel> channelCache = new ConcurrentHashMap<>();
    //等待响应的Latch映射：channelId -> CountDownLatch
    private final ConcurrentHashMap<Integer, CountDownLatch> pendingLatches = new ConcurrentHashMap<>();
    //接收到的响应映射：channelId -> RpcResponse
    private final ConcurrentHashMap<Integer, RpcResponse> responseMap = new ConcurrentHashMap<>();
    //请求ID生成器
    private final AtomicInteger requestIdGenerator = new AtomicInteger(0);

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

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        //生成唯一请求ID（在try外声明，使catch块可访问用于日志）
        int channelId = requestIdGenerator.incrementAndGet();
        request.setChannelId(channelId);
        try {

            //获取或创建该服务对应的Channel（连接复用）
            Channel channel = getOrCreateChannel(request.getInterfaceName());

            //创建CountDownLatch并注册到pendingLatches
            CountDownLatch latch = new CountDownLatch(1);
            pendingLatches.put(channelId, latch);

            //写出请求（不关闭通道，支持连接复用）
            channel.writeAndFlush(request);

            //阻塞等待响应，保持原有同步语义
            if (!latch.await(REQUEST_TIMEOUT, TimeUnit.SECONDS)) {
                pendingLatches.remove(channelId);
                responseMap.remove(channelId);
                logger.warn("请求超时，channelId={}", channelId);
                return null;
            }

            RpcResponse response = responseMap.remove(channelId);
            logger.debug("收到响应: {}", response);
            return response;
        } catch (Exception e) {
            logger.error("发送请求失败，channelId={}", channelId, e);
            return null;
        }
    }

    /**
     * NettyClientHandler收到响应时调用，根据channelId找到对应的Latch并计数释放。
     */
    public void handleResponse(RpcResponse response) {
        int channelId = response.getChannelId();
        //先放入responseMap，再释放latch，避免getNow时race
        responseMap.put(channelId, response);
        CountDownLatch latch = pendingLatches.remove(channelId);
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * Channel异常关闭时调用，释放该Channel上所有等待中的请求。
     */
    public void handleChannelInactive(String serviceName) {
        //从缓存中移除不活跃的连接，下次请求会重建
        channelCache.remove(serviceName);
        //注意：此处无法精确知道哪些channelId在该channel上，
        //但客户端会因连接断开收到exceptionCaught，由handler处理单个请求的失败
    }
}

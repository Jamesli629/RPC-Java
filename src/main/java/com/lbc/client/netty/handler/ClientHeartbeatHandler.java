package com.lbc.client.netty.handler;

import com.lbc.client.rpcClient.impl.NettyRpcClient;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端心跳与断连检测处理器
 *
 * 利用 Netty 的 IdleStateHandler 检测写空闲和连接断开：
 * - channelInactive：连接断开时通知 NettyRpcClient 清理 channelCache
 */
public class ClientHeartbeatHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClientHeartbeatHandler.class);

    private final NettyRpcClient rpcClient;

    public ClientHeartbeatHandler(NettyRpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.warn("客户端通道关闭，远程地址: {}", ctx.channel().remoteAddress());
        // 通知 NettyRpcClient 清理 channelCache 和 pendingFutures
        rpcClient.handleChannelInactive(ctx.channel().remoteAddress().toString());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("客户端通道异常，远程地址: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
        rpcClient.handleChannelInactive(ctx.channel().remoteAddress().toString());
        super.exceptionCaught(ctx, cause);
    }
}

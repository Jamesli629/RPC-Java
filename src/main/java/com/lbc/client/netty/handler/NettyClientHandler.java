package com.lbc.client.netty.handler;

import com.lbc.client.rpcClient.impl.NettyRpcClient;
import com.lbc.common.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lbc
 * @date 2024/09/16 15:46
 *
 * 客户端Netty处理器：收到响应后通过channelId回调完成对应的等待请求。
 * 不关闭通道，支持连接复用。
 **/
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    private static final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);

    private final NettyRpcClient rpcClient;

    public NettyClientHandler(NettyRpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) throws Exception {
        // 通过channelId回调完成对应的等待请求，不关闭通道（连接复用）
        rpcClient.handleResponse(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //异常处理：关闭通道，下次请求会重建连接
        logger.error("客户端通道异常", cause);
        ctx.close();
    }
}

package com.lbc.server.netty.handler;

import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import com.lbc.server.provider.ServiceProvider;
import com.lbc.server.rateLimit.RateLimit;
import com.lbc.server.server.impl.NettyRPCRPCServer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Lbc
 * @date 2024/09/16 15:53
 *
 * 支持优雅下线的 RPC 服务端处理器
 **/
public class NettyRPCServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private static final Logger logger = LoggerFactory.getLogger(NettyRPCServerHandler.class);

    private ServiceProvider serviceProvider;
    private NettyRPCRPCServer server;

    public NettyRPCServerHandler(ServiceProvider serviceProvider, NettyRPCRPCServer server) {
        this.serviceProvider = serviceProvider;
        this.server = server;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        // 优雅下线：排空状态下拒绝新请求
        if (server.isDraining()) {
            logger.warn("服务端正在下线，拒绝新请求，channelId={}", request.getChannelId());
            RpcResponse response = RpcResponse.builder()
                    .code(503)
                    .message("服务正在下线，请稍后重试")
                    .channelId(request.getChannelId())
                    .build();
            ctx.writeAndFlush(response);
            return;
        }

        // 请求开始计数
        server.onRequestStart();

        try {
            //接收request，读取并调用服务
            RpcResponse response = getResponse(request);
            // 不复用连接时不关闭通道，支持连接复用
            ctx.writeAndFlush(response);
        } finally {
            // 请求结束计数
            server.onRequestEnd();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("服务端通道异常", cause);
        ctx.close();
    }

    private RpcResponse getResponse(RpcRequest rpcRequest) {
        //得到服务名
        String interfaceName = rpcRequest.getInterfaceName();
        //接口限流降级
        RateLimit rateLimit = serviceProvider.getRateLimitProvider().getRateLimit(interfaceName);
        if (!rateLimit.getToken()) {
            //如果获取令牌失败，进行限流降级，快速返回结果
            logger.warn("服务限流!! 接口: {}", interfaceName);
            RpcResponse response = RpcResponse.fail();
            response.setChannelId(rpcRequest.getChannelId());
            return response;
        }
        //得到服务端相应服务实现类
        Object service = serviceProvider.getService(interfaceName);
        //反射调用方法
        Method method = null;
        try {
            method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamsType());
            Object invoke = method.invoke(service, rpcRequest.getParams());
            RpcResponse response = RpcResponse.sussess(invoke);
            //回传channelId，使客户端能匹配到对应的等待请求
            response.setChannelId(rpcRequest.getChannelId());
            return response;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error("方法执行错误，接口: {}, 方法: {}", interfaceName, rpcRequest.getMethodName(), e);
            RpcResponse response = RpcResponse.fail();
            response.setChannelId(rpcRequest.getChannelId());
            return response;
        }
    }
}

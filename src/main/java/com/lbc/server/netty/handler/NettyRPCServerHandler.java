package com.lbc.server.netty.handler;

import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import com.lbc.server.provider.ServiceProvider;
import com.lbc.server.rateLimit.RateLimit;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Lbc
 * @date 2024/09/16 15:53
 **/
@AllArgsConstructor
public class NettyRPCServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private static final Logger logger = LoggerFactory.getLogger(NettyRPCServerHandler.class);

    private ServiceProvider serviceProvider;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        //接收request，读取并调用服务
        RpcResponse response = getResponse(request);
        ctx.writeAndFlush(response);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
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

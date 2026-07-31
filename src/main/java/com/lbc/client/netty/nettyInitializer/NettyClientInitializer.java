package com.lbc.client.netty.nettyInitializer;

import com.lbc.client.netty.handler.ClientHeartbeatHandler;
import com.lbc.client.netty.handler.NettyClientHandler;
import com.lbc.client.rpcClient.impl.NettyRpcClient;
import com.lbc.common.serializer.myCode.MyDecoder;
import com.lbc.common.serializer.myCode.MyEncoder;
import com.lbc.common.serializer.mySerializer.JsonSerializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * @author Lbc
 * @date 2024/09/16 15:45
 *
 * 客户端 Channel 初始化器，装配空闲检测、心跳、编解码器和业务处理器。
 * IdleStateHandler 检测写空闲和连接断开，及时清理失效 Channel。
 * 需要传入 NettyRpcClient 引用，以便 Handler 回调完成等待中的请求。
 **/
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    private final NettyRpcClient rpcClient;

    public NettyClientInitializer(NettyRpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        // 15秒写空闲检测：若连接已断则触发 channelInactive，清理 channelCache
        pipeline.addLast(new IdleStateHandler(0, 15, 0, TimeUnit.SECONDS));
        // 心跳与断连检测处理器
        pipeline.addLast(new ClientHeartbeatHandler(rpcClient));
        //使用自定义的编/解码器
        pipeline.addLast(new MyDecoder());
        pipeline.addLast(new MyEncoder(new JsonSerializer()));
        pipeline.addLast(new NettyClientHandler(rpcClient));
    }
}

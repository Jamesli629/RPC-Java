package com.lbc.client.netty.nettyInitializer;

import com.lbc.client.netty.handler.NettyClientHandler;
import com.lbc.client.rpcClient.impl.NettyRpcClient;
import com.lbc.common.serializer.myCode.MyDecoder;
import com.lbc.common.serializer.myCode.MyEncoder;
import com.lbc.common.serializer.mySerializer.JsonSerializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;


/**
 * @author Lbc
 * @date 2024/09/16 15:45
 *
 * 客户端Channel初始化器，装配自定义编解码器和业务处理器。
 * 需要传入NettyRpcClient引用，以便Handler回调完成等待中的请求。
 **/
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    private final NettyRpcClient rpcClient;

    public NettyClientInitializer(NettyRpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        //使用自定义的编/解码器
        pipeline.addLast(new MyDecoder());
        pipeline.addLast(new MyEncoder(new JsonSerializer()));
        pipeline.addLast(new NettyClientHandler(rpcClient));
    }
}

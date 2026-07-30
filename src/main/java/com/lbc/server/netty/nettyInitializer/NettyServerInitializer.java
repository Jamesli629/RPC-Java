package com.lbc.server.netty.nettyInitializer;

import com.lbc.common.serializer.myCode.MyDecoder;
import com.lbc.common.serializer.myCode.MyEncoder;
import com.lbc.common.serializer.mySerializer.JsonSerializer;
import com.lbc.server.netty.handler.NettyRPCServerHandler;
import com.lbc.server.provider.ServiceProvider;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.AllArgsConstructor;

import java.util.concurrent.TimeUnit;

/**
 * @author Lbc
 * @date 2024/09/16 15:55
 **/
@AllArgsConstructor
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {
    private ServiceProvider serviceProvider;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        // 30秒无读事件触发空闲检测（连接复用场景下清理僵尸连接）
        pipeline.addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
        //使用自定义的编/解码器
        pipeline.addLast(new MyEncoder(new JsonSerializer()));
        pipeline.addLast(new MyDecoder());
        pipeline.addLast(new NettyRPCServerHandler(serviceProvider));
    }
}

package com.lbc.common.serializer.myCode;

import com.lbc.common.message.MessageType;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import com.lbc.common.serializer.mySerializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.AllArgsConstructor;

/**
 * @author Lbc
 * @date 2024/09/21 10:17
 **/
@AllArgsConstructor
public class MyEncoder extends MessageToByteEncoder {
    private Serializer serializer;
    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        //1.写入消息类型
        if(msg instanceof RpcRequest){
            out.writeShort(MessageType.REQUEST.getCode());
        }
        else if(msg instanceof RpcResponse){
            out.writeShort(MessageType.RESPONSE.getCode());
        }
        //2.写入序列化方式
        out.writeShort(serializer.getType());
        //得到序列化数组
        byte[] serializeBytes = serializer.serialize(msg);
        //3.写入长度
        out.writeInt(serializeBytes.length);
        //4.写入请求/响应唯一标识channelId，用于连接池中匹配请求与响应
        int channelId = 0;
        if (msg instanceof RpcRequest) {
            channelId = ((RpcRequest) msg).getChannelId();
        } else if (msg instanceof RpcResponse) {
            channelId = ((RpcResponse) msg).getChannelId();
        }
        out.writeInt(channelId);
        //5.写入序列化数组
        out.writeBytes(serializeBytes);
    }
}

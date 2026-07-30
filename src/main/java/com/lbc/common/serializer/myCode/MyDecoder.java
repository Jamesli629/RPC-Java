package com.lbc.common.serializer.myCode;

import com.lbc.common.message.MessageType;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import com.lbc.common.serializer.mySerializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * @author Lbc
 * @date 2024/09/21 10:18
 **/
public class MyDecoder extends ByteToMessageDecoder {
    // messageType(2) + serializerType(2) + length(4) + channelId(4) = 12
    private static final int HEADER_LENGTH = 12;

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) throws Exception {
        // Wait until full header arrives.
        if (in.readableBytes() < HEADER_LENGTH) {
            return;
        }
        in.markReaderIndex();

        short messageType = in.readShort();
        if (messageType != MessageType.REQUEST.getCode() &&
                messageType != MessageType.RESPONSE.getCode()) {
            System.out.println("Unsupported message type");
            return;
        }

        short serializerType = in.readShort();
        Serializer serializer = Serializer.getSerializerByCode(serializerType);
        if (serializer == null) {
            throw new RuntimeException("Serializer not found");
        }

        int length = in.readInt();
        if (length < 0) {
            throw new RuntimeException("Illegal message length: " + length);
        }

        int channelId = in.readInt();

        // Half packet: reset and wait for remaining bytes.
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        Object deserialize = serializer.deserialize(bytes, messageType);

        // 将channelId设置到反序列化后的对象上，用于连接池中匹配请求与响应
        if (deserialize instanceof RpcRequest) {
            ((RpcRequest) deserialize).setChannelId(channelId);
        } else if (deserialize instanceof RpcResponse) {
            ((RpcResponse) deserialize).setChannelId(channelId);
        }

        out.add(deserialize);
    }
}

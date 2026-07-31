package com.lbc.common.serializer.myCode;

import com.lbc.common.config.ConfigManager;
import com.lbc.common.message.MessageType;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import com.lbc.common.serializer.mySerializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.AllArgsConstructor;

import java.util.zip.CRC32;

/**
 * @author Lbc
 * @date 2024/09/21 10:17
 *
 * 协议编码器，支持可选的 CRC32 校验和
 **/
@AllArgsConstructor
public class MyEncoder extends MessageToByteEncoder {
    private Serializer serializer;

    private static final boolean CRC_ENABLED = ConfigManager.getInstance()
            .getBoolean("rpc.protocol.crc.enabled", true);

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        //1.写入 Magic Number（协议起始标识）
        out.writeShort(ProtocolConstants.MAGIC_NUMBER);
        //2.写入协议版本
        out.writeShort(ProtocolConstants.PROTOCOL_VERSION);
        //3.写入消息类型
        if(msg instanceof RpcRequest){
            out.writeShort(MessageType.REQUEST.getCode());
        }
        else if(msg instanceof RpcResponse){
            out.writeShort(MessageType.RESPONSE.getCode());
        }
        //4.写入序列化方式
        out.writeShort(serializer.getType());
        //得到序列化数组
        byte[] serializeBytes = serializer.serialize(msg);
        //5.写入长度
        out.writeInt(serializeBytes.length);
        //6.写入请求/响应唯一标识channelId，用于连接池中匹配请求与响应
        int channelId = 0;
        if (msg instanceof RpcRequest) {
            channelId = ((RpcRequest) msg).getChannelId();
        } else if (msg instanceof RpcResponse) {
            channelId = ((RpcResponse) msg).getChannelId();
        }
        out.writeInt(channelId);
        //7.写入序列化数组
        out.writeBytes(serializeBytes);
        //8.写入 CRC32 校验和（可选）
        if (CRC_ENABLED) {
            CRC32 crc32 = new CRC32();
            crc32.update(serializeBytes);
            out.writeInt((int) crc32.getValue());
        }
    }
}

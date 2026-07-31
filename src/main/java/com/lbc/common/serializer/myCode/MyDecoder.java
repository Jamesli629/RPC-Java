package com.lbc.common.serializer.myCode;

import com.lbc.common.config.ConfigManager;
import com.lbc.common.message.MessageType;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import com.lbc.common.serializer.mySerializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.zip.CRC32;

/**
 * @author Lbc
 * @date 2024/09/21 10:18
 *
 * 协议帧格式（20 字节头 + payload + CRC）：
 * magicNumber(2) + version(2) + messageType(2) + serializerType(2) + length(4) + channelId(4) + payload(length) + crc32(4)
 **/
public class MyDecoder extends ByteToMessageDecoder {
    private static final Logger logger = LoggerFactory.getLogger(MyDecoder.class);

    private static final boolean CRC_ENABLED = ConfigManager.getInstance()
            .getBoolean("rpc.protocol.crc.enabled", true);

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) throws Exception {
        // Wait until full header arrives.
        if (in.readableBytes() < ProtocolConstants.HEADER_LENGTH) {
            return;
        }
        in.markReaderIndex();

        // 校验 Magic Number，不匹配则关闭连接（非法连接/协议错误）
        short magicNumber = in.readShort();
        if (magicNumber != ProtocolConstants.MAGIC_NUMBER) {
            logger.warn("非法协议连接，Magic Number=0x{}，期望=0x{}，远程地址: {}",
                    Integer.toHexString(magicNumber & 0xFFFF),
                    Integer.toHexString(ProtocolConstants.MAGIC_NUMBER & 0xFFFF),
                    channelHandlerContext.channel().remoteAddress());
            channelHandlerContext.close();
            return;
        }

        // 校验协议版本，不支持的版本直接拒绝
        short version = in.readShort();
        if (version > ProtocolConstants.MAX_PROTOCOL_VERSION) {
            logger.warn("不支持的协议版本: {}，最大支持版本: {}，远程地址: {}",
                    version, ProtocolConstants.MAX_PROTOCOL_VERSION,
                    channelHandlerContext.channel().remoteAddress());
            channelHandlerContext.close();
            return;
        }

        short messageType = in.readShort();
        if (messageType != MessageType.REQUEST.getCode() &&
                messageType != MessageType.RESPONSE.getCode()) {
            logger.warn("不支持的消息类型: {}，远程地址: {}", messageType,
                    channelHandlerContext.channel().remoteAddress());
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

        // CRC32 校验（可选）
        if (CRC_ENABLED) {
            if (in.readableBytes() < ProtocolConstants.CRC_SIZE) {
                in.resetReaderIndex();
                return;
            }
            int receivedCrc = in.readInt();
            CRC32 crc32 = new CRC32();
            crc32.update(bytes);
            int expectedCrc = (int) crc32.getValue();
            if (receivedCrc != expectedCrc) {
                String remoteAddr = "unknown";
                try {
                    if (channelHandlerContext != null && channelHandlerContext.channel() != null) {
                        remoteAddr = String.valueOf(channelHandlerContext.channel().remoteAddress());
                    }
                } catch (Exception ignored) {
                }
                logger.warn("CRC 校验失败: 收到=0x{}, 期望=0x{}, 远程地址: {}",
                        Integer.toHexString(receivedCrc),
                        Integer.toHexString(expectedCrc),
                        remoteAddr);
                return;
            }
        }

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

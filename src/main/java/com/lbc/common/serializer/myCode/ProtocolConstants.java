package com.lbc.common.serializer.myCode;

/**
 * RPC 协议常量定义
 *
 * 协议帧格式（16 字节头）：
 * magicNumber(2) + version(2) + messageType(2) + serializerType(2) + length(4) + channelId(4)
 */
public class ProtocolConstants {
    /** 协议起始标识，用于快速识别非法连接 */
    public static final short MAGIC_NUMBER = (short) 0xCAFE;

    /** 当前协议版本 */
    public static final short PROTOCOL_VERSION = 0x0001;

    /** 支持的最大协议版本 */
    public static final short MAX_PROTOCOL_VERSION = 0x0001;

    /** 帧头总长度（字节） */
    public static final int HEADER_LENGTH = 16;

    private ProtocolConstants() {
    }
}

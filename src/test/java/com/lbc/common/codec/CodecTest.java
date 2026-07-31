package com.lbc.common.codec;

import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import com.lbc.common.serializer.myCode.CodecTestHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编解码 + CRC 校验测试
 */
public class CodecTest {

    @Test
    public void testEncodeAndDecodeRequest() throws Exception {
        RpcRequest request = RpcRequest.builder()
                .channelId(42)
                .interfaceName("com.lbc.service.UserService")
                .methodName("getUserByUserId")
                .params(new Object[]{1})
                .paramsType(new Class[]{Integer.class})
                .build();

        ByteBuf buf = Unpooled.buffer();
        CodecTestHelper.encode(request, buf);

        Object decoded = CodecTestHelper.decode(buf);
        assertTrue(decoded instanceof RpcRequest);

        RpcRequest result = (RpcRequest) decoded;
        assertEquals(42, result.getChannelId());
        assertEquals("com.lbc.service.UserService", result.getInterfaceName());
        assertEquals("getUserByUserId", result.getMethodName());
    }

    @Test
    public void testEncodeAndDecodeResponse() throws Exception {
        RpcResponse response = RpcResponse.sussess("test data");
        response.setChannelId(99);

        ByteBuf buf = Unpooled.buffer();
        CodecTestHelper.encode(response, buf);

        Object decoded = CodecTestHelper.decode(buf);
        assertTrue(decoded instanceof RpcResponse);

        RpcResponse result = (RpcResponse) decoded;
        assertEquals(99, result.getChannelId());
        assertEquals(200, result.getCode());
    }

    @Test
    public void testMagicNumberValidation() throws Exception {
        // 写入非法 Magic Number
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(0xDEAD); // 错误的 Magic Number
        buf.writeShort(1);
        buf.writeShort(0);
        buf.writeShort(1);
        buf.writeInt(0);
        buf.writeInt(0);

        // 解码应该返回 null（非法连接被拒绝）
        Object decoded = CodecTestHelper.decode(buf);
        assertNull(decoded);
    }

    @Test
    public void testCrcValidation() throws Exception {
        // 构造一个 payload 并计算正确 CRC
        RpcRequest request = RpcRequest.builder()
                .channelId(1)
                .methodName("test")
                .build();

        ByteBuf buf = Unpooled.buffer();
        CodecTestHelper.encode(request, buf);

        // 篡改最后一个字节（CRC 部分）
        int crcIndex = buf.writerIndex() - 1;
        buf.setByte(crcIndex, buf.getByte(crcIndex) ^ 0xFF);

        // CRC 校验失败，应该返回 null
        Object decoded = CodecTestHelper.decode(buf);
        assertNull(decoded);
    }
}

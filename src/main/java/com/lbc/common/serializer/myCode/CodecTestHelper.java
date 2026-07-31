package com.lbc.common.serializer.myCode;

import com.lbc.common.serializer.mySerializer.JsonSerializer;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 编解码测试辅助类
 *
 * 将 protected 的 encode/decode 方法暴露为 public，便于单元测试。
 */
public class CodecTestHelper {

    private static final MyEncoder ENCODER = new MyEncoder(new JsonSerializer());
    private static final MyDecoder DECODER = new MyDecoder();

    /**
     * 编码消息到 ByteBuf
     */
    public static void encode(Object msg, ByteBuf out) throws Exception {
        ENCODER.encode(null, msg, out);
    }

    /**
     * 从 ByteBuf 解码消息
     *
     * @return 解码后的对象，如果数据不足或校验失败返回 null
     */
    public static Object decode(ByteBuf in) throws Exception {
        List<Object> out = new ArrayList<>();
        DECODER.decode(null, in, out);
        return out.isEmpty() ? null : out.get(0);
    }
}

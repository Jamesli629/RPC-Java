package com.lbc.common.serializer;

import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;
import com.lbc.common.pojo.User;
import com.lbc.common.serializer.mySerializer.JsonSerializer;
import com.lbc.common.serializer.mySerializer.KryoSerializer;
import com.lbc.common.serializer.mySerializer.ObjectSerializer;
import com.lbc.common.serializer.mySerializer.Serializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 序列化/反序列化测试
 */
public class SerializerTest {

    @Test
    public void testJsonSerializerRequest() {
        JsonSerializer serializer = new JsonSerializer();
        RpcRequest request = RpcRequest.builder()
                .channelId(1)
                .interfaceName("com.lbc.service.UserService")
                .methodName("getUserByUserId")
                .params(new Object[]{1})
                .paramsType(new Class[]{Integer.class})
                .build();

        byte[] bytes = serializer.serialize(request);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        Object deserialized = serializer.deserialize(bytes, 0);
        assertTrue(deserialized instanceof RpcRequest);
        RpcRequest result = (RpcRequest) deserialized;
        assertEquals(1, result.getChannelId());
        assertEquals("getUserByUserId", result.getMethodName());
    }

    @Test
    public void testJsonSerializerResponse() {
        JsonSerializer serializer = new JsonSerializer();
        User user = User.builder().id(1).userName("test").sex(true).build();
        RpcResponse response = RpcResponse.sussess(user);

        byte[] bytes = serializer.serialize(response);
        assertNotNull(bytes);

        Object deserialized = serializer.deserialize(bytes, 1);
        assertTrue(deserialized instanceof RpcResponse);
        RpcResponse result = (RpcResponse) deserialized;
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    public void testObjectSerializer() {
        ObjectSerializer serializer = new ObjectSerializer();
        RpcRequest request = RpcRequest.builder()
                .channelId(2)
                .methodName("testMethod")
                .build();

        byte[] bytes = serializer.serialize(request);
        assertNotNull(bytes);

        Object deserialized = serializer.deserialize(bytes, 0);
        assertTrue(deserialized instanceof RpcRequest);
        assertEquals(2, ((RpcRequest) deserialized).getChannelId());
    }

    @Test
    public void testKryoSerializer() {
        KryoSerializer serializer = new KryoSerializer();
        assertEquals(3, serializer.getType());

        RpcResponse response = RpcResponse.sussess("hello kryo");
        byte[] bytes = serializer.serialize(response);
        assertNotNull(bytes);

        Object deserialized = serializer.deserialize(bytes, 1);
        assertTrue(deserialized instanceof RpcResponse);
        assertEquals(200, ((RpcResponse) deserialized).getCode());
    }

    @Test
    public void testSerializerByCode() {
        Serializer s0 = Serializer.getSerializerByCode(0);
        assertNotNull(s0);
        assertEquals(0, s0.getType());

        Serializer s1 = Serializer.getSerializerByCode(1);
        assertNotNull(s1);
        assertEquals(1, s1.getType());

        Serializer s3 = Serializer.getSerializerByCode(3);
        assertNotNull(s3);
        assertEquals(3, s3.getType());
    }

    @Test
    public void testRpcResponseFail() {
        RpcResponse fail = RpcResponse.fail();
        assertEquals(500, fail.getCode());
        assertEquals("服务器发生错误", fail.getMessage());
        assertNull(fail.getData());
    }

    @Test
    public void testRpcResponseFailWithException() {
        RuntimeException ex = new RuntimeException("测试异常");
        RpcResponse fail = RpcResponse.failWithException(ex);
        assertEquals(500, fail.getCode());
        assertNotNull(fail.getExceptionClass());
        assertTrue(fail.getExceptionClass().contains("RuntimeException"));
        assertEquals("测试异常", fail.getExceptionMessage());
    }
}

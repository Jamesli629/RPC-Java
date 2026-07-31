package com.lbc.common.serializer.mySerializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.lbc.common.message.RpcRequest;
import com.lbc.common.message.RpcResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Kryo 高性能序列化实现
 *
 * 特点：
 * - 比 Java 原生序列化快 10 倍以上
 * - 比 JSON 序列化更紧凑
 * - 纯 Java，无额外依赖冲突
 *
 * 注意：Kryo 默认不保证跨版本兼容，生产环境需注册所有序列化类。
 */
public class KryoSerializer implements Serializer {

    // Kryo 实例非线程安全，使用 ThreadLocal
    private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // 注册常用类，提升性能并保证兼容性
        kryo.register(RpcRequest.class);
        kryo.register(RpcResponse.class);
        kryo.register(Object[].class);
        kryo.register(Class[].class);
        // 允许未注册的类（开发方便，生产环境建议全部注册）
        kryo.setRegistrationRequired(false);
        // 支持循环引用
        kryo.setReferences(true);
        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) {
        Kryo kryo = KRYO_THREAD_LOCAL.get();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Output output = new Output(bos);
        kryo.writeClassAndObject(output, obj);
        output.flush();
        return bos.toByteArray();
    }

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        Kryo kryo = KRYO_THREAD_LOCAL.get();
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        Input input = new Input(bis);
        return kryo.readClassAndObject(input);
    }

    //3 代表 Kryo 序列化方式
    @Override
    public int getType() {
        return 3;
    }
}

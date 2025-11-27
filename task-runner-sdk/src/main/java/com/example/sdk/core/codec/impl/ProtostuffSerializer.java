package com.example.sdk.core.codec.impl;

import com.example.sdk.core.codec.Serializer;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protostuff序列化实现
 *
 * @author 0xNPC
 */
public class ProtostuffSerializer implements Serializer {

    private static final Map<Class<?>, Schema<?>> cachedSchemas = new ConcurrentHashMap<>();

    private static <T> Schema<T> getSchema(Class<T> clazz) {
        return (Schema<T>) cachedSchemas.computeIfAbsent(clazz, RuntimeSchema::getSchema);
    }

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        Class clazz = obj.getClass();
        LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
        try {
            return ProtostuffIOUtil.toByteArray(obj, getSchema(clazz), buffer);
        } finally {
            buffer.clear();
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            T message = clazz.newInstance();
            ProtostuffIOUtil.mergeFrom(bytes, message, getSchema(clazz));
            return message;
        } catch (Exception e) {
            throw new RuntimeException("反序列化失败", e);
        }
    }

}

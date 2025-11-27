package com.example.sdk.core.codec;

/**
 * 序列化实现
 *
 * @author 0xNPC
 */
public interface Serializer {

    byte[] serialize(Object obj);

    <T> T deserialize(byte[] bytes, Class<T> clazz);

}

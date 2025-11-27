package com.example.sdk.core;

import com.example.sdk.core.codec.Serializer;
import com.example.sdk.core.codec.impl.JsonSerializer;
import com.example.sdk.core.codec.impl.ProtostuffSerializer;

/**
 * 静态常量
 *
 * @author 0xNPC
 */
public interface Constants {

    /**
     * 用于校验数据包合法性
     */
    byte MAGIC = (byte) 0xB1;

    /**
     * 通信协议版本
     */
    byte VERSION = 1;

    /**
     * 协议头长度: Magic(1) + Ver(1) + Serializer(1) + Type(1) + ReqId(8) + BodyLen(4) = 16
     */
    int HEADER_LENGTH = 16;


    // **************** 序列化器实例 ****************
    /**
     * Protostuff序列化实现
     */
    Serializer PROTO_SERIALIZER = new ProtostuffSerializer();
    /**
     * JSON序列化实现
     */
    Serializer JSON_SERIALIZER = new JsonSerializer();

}

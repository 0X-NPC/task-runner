package com.example.sdk.core.codec;

/**
 * 消息序列化实现类型
 *
 * @author 0xNPC
 */
public enum SerializerType {
    PROTOSTUFF((byte) 1),
    JSON((byte) 2);

    byte code;

    SerializerType(byte code) {
        this.code = code;
    }

    public static SerializerType fromCode(byte code) {
        return code == 2 ? JSON : PROTOSTUFF;
    }

}

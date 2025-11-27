package com.example.sdk.core.codec.impl;

import com.example.sdk.core.codec.Serializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Jackson JSON 序列化实现
 *
 * @author 0xNPC
 */
@Slf4j
public class JsonSerializer implements Serializer {

    private final ObjectMapper mapper;

    public JsonSerializer() {
        this.mapper = new ObjectMapper();
        // 忽略未知属性，增强兼容性
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        try {
            return mapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON Serialize error", e);
            throw new RuntimeException("JSON Serialize error", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(bytes, clazz);
        } catch (IOException e) {
            log.error("JSON Deserialize error", e);
            throw new RuntimeException("JSON Deserialize error", e);
        }
    }
}
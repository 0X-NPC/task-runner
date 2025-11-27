package com.example.sdk.core.command;

import com.example.sdk.core.codec.SerializerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 传输对象
 *
 * @author 0xNPC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemotingCommand {

    private long requestId;
    private CommandType type;
    private SerializerType serializerType;
    /**
     * 原始对象，比如：workerId
     */
    private Object bodyObj;

    /**
     * 序列化后的字节数据 (用于 Zero Copy 发送)
     */
    private transient byte[] cachedBodyBytes;

    /**
     * 创建请求 (默认 Protostuff)
     */
    public static RemotingCommand createRequest(CommandType type, Object body) {
        return createRequest(type, body, SerializerType.PROTOSTUFF);
    }

    /**
     * 创建请求 (指定序列化方式)
     */
    public static RemotingCommand createRequest(CommandType type, Object body, SerializerType serializerType) {
        return builder()
                .requestId(RequestIdGenerator.nextId())
                .type(type)
                .serializerType(serializerType)
                .bodyObj(body)
                .build();
    }

    /**
     * 创建响应 (默认 Protostuff)
     */
    public static RemotingCommand createResponse(long reqId, CommandType type, Object body) {
        return createResponse(reqId, type, body, SerializerType.PROTOSTUFF);
    }

    /**
     * 创建响应 (指定序列化方式)
     */
    public static RemotingCommand createResponse(long reqId, CommandType type, Object body, SerializerType serializerType) {
        return builder()
                .requestId(reqId)
                .type(type)
                .serializerType(serializerType)
                .bodyObj(body)
                .build();
    }

}

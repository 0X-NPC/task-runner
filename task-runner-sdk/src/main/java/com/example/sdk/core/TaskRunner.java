package com.example.sdk.core;

import com.example.sdk.core.codec.SerializerType;
import com.example.sdk.core.command.RemotingCommand;

import static com.example.sdk.core.Constants.JSON_SERIALIZER;
import static com.example.sdk.core.Constants.PROTO_SERIALIZER;

/**
 * 任务处理支持
 *
 * @author 0xNPC
 */
public class TaskRunner {

    /**
     * 辅助方法：从命令中提取对象，支持 JSON 和 Protostuff
     */
    public static <T> T decodeBody(RemotingCommand cmd, Class<T> clazz) {
        if (cmd.getCachedBodyBytes() == null || cmd.getCachedBodyBytes().length == 0) {
            return null;
        }
        // 支持 Protostuff
        if (cmd.getSerializerType() == SerializerType.PROTOSTUFF) {
            return PROTO_SERIALIZER.deserialize(cmd.getCachedBodyBytes(), clazz);
        }
        // 新增支持 JSON
        else if (cmd.getSerializerType() == SerializerType.JSON) {
            return JSON_SERIALIZER.deserialize(cmd.getCachedBodyBytes(), clazz);
        }
        return null;
    }

}
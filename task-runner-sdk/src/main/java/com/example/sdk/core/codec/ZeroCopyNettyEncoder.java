package com.example.sdk.core.codec;

import com.example.sdk.core.command.RemotingCommand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

import static com.example.sdk.core.Constants.*;

/**
 * 零拷贝的 Netty 编码器
 * <p>
 * 替换 MessageToByteEncoder，直接输出 ByteBuf 列表
 *
 * @author 0xNPC
 */
public class ZeroCopyNettyEncoder extends MessageToMessageEncoder<RemotingCommand> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RemotingCommand msg, List<Object> out) {
        // 1. 序列化 Body
        byte[] body = msg.getCachedBodyBytes();
        if (body == null && msg.getBodyObj() != null) {
            if (msg.getSerializerType() == SerializerType.PROTOSTUFF) {
                body = PROTO_SERIALIZER.serialize(msg.getBodyObj());
            } else if (msg.getSerializerType() == SerializerType.JSON) {
                body = JSON_SERIALIZER.serialize(msg.getBodyObj());
            } else {
                body = new byte[0];
            }
        }
        if (body == null) {
            body = new byte[0];
        }

        // 2. 构建 Header ByteBuf
        ByteBuf header = ctx.alloc().buffer(HEADER_LENGTH);
        header.writeByte(MAGIC);
        header.writeByte(VERSION);
        header.writeByte(msg.getSerializerType().code);
        header.writeByte(msg.getType().ordinal());
        header.writeLong(msg.getRequestId());
        header.writeInt(body.length);

        // 3. 包装 Body ByteBuf (零拷贝)
        // wrappedBuffer 不会复制数组，而是直接包装
        ByteBuf bodyBuf = Unpooled.wrappedBuffer(body);

        // 4. 使用 CompositeByteBuf 组合 Header 和 Body (逻辑组合，不物理拷贝)
        // 或者直接将两个 Buf 加入 out 列表，Netty 会处理写出
        // 推荐直接 add 两个对象，Netty 的 Gathering Write 会优化
        out.add(header);
        if (body.length > 0) {
            out.add(bodyBuf);
        } else {
            // 如果 body 为空，bodyBuf 也是空的，释放它或者不添加
            bodyBuf.release();
        }
    }
}
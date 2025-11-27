package com.example.sdk.core.codec;

import com.example.sdk.core.command.RemotingCommand;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import static com.example.sdk.core.Constants.*;

/**
 * Netty编码器
 *
 * @author 0xNPC
 */
public class NettyEncoder extends MessageToByteEncoder<RemotingCommand> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RemotingCommand msg, ByteBuf out) {
        // 1. 序列化 Body
        byte[] body = msg.getCachedBodyBytes();
        if (body == null && msg.getBodyObj() != null) {
            if (msg.getSerializerType() == SerializerType.PROTOSTUFF) {
                body = PROTO_SERIALIZER.serialize(msg.getBodyObj());

            } else if (msg.getSerializerType() == SerializerType.JSON) {
                // 增加 JSON 序列化支持
                body = JSON_SERIALIZER.serialize(msg.getBodyObj());

            } else {
                // JSON fallback omitted for brevity
                body = new byte[0];
            }
        }
        if (body == null) body = new byte[0];

        // 2. 写入 Header
        out.writeByte(MAGIC);
        out.writeByte(VERSION);
        out.writeByte(msg.getSerializerType().code);
        out.writeByte(msg.getType().ordinal());
        out.writeLong(msg.getRequestId());
        out.writeInt(body.length);

        // 3. 写入 Body
        // 提示：Netty 的 MessageToByteEncoder 会自动处理 buffer，
        // 如果 body 很大，可以使用 ctx.write(Unpooled.wrappedBuffer(body)) 绕过堆内拷贝，
        // 但需要改为继承 MessageToMessageEncoder。此处为简化仍用 writeBytes。
        out.writeBytes(body);
    }

}

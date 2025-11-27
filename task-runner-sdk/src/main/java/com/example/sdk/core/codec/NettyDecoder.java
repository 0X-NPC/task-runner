package com.example.sdk.core.codec;

import com.example.sdk.core.command.CommandType;
import com.example.sdk.core.command.RemotingCommand;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * Netty解码器
 * <p>
 * 通过长度字段法实现<code>LengthFieldBasedFrameDecoder</code>处理了TCP粘包/拆包问题
 * <p>
 * <b>协议格式:</b>
 * <pre>Magic(1) + Ver(1) + Serializer(1) + Type(1) + ReqId(8) + BodyLen(4)</pre>
 *
 * @author 0xNPC
 */
public class NettyDecoder extends LengthFieldBasedFrameDecoder {

    public NettyDecoder() {
        // maxFrameLength: 10M, 单个包最大限制，防攻击
        // lengthFieldOffset: 12, 长度字段在第几个字节，(Magic 1 + Ver 1 + Serializer 1 + Type 1 + ReqId 8 = 12)
        // lengthFieldLength: 4: 长度字段占几个字节？(int 是 4)
        // lengthAdjustment
        // initialBytesToStrip
        super(10 * 1024 * 1024, 12, 4, 0, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            byte magic = frame.readByte();
            byte version = frame.readByte();
            byte serializerCode = frame.readByte();
            byte typeOrdinal = frame.readByte();
            long requestId = frame.readLong();
            int bodyLength = frame.readInt();

            // 内存管理
            // 使用 readRetainedSlice 避免数据拷贝，直接传递 Buffer 引用（需小心释放）
            // 考虑到易用性，SDK 内部将 buffer 转为 byte[] 或直接反序列化
            byte[] bodyBytes = new byte[bodyLength];
            frame.readBytes(bodyBytes);

            RemotingCommand cmd = RemotingCommand.builder()
                    .requestId(requestId)
                    .type(CommandType.values()[typeOrdinal])
                    .serializerType(SerializerType.fromCode(serializerCode))
                    .build();

            // 延迟反序列化：先把 bytes 存起来，业务层按需反序列化，或者在此处基于 Type 预判
            cmd.setCachedBodyBytes(bodyBytes);

            return cmd;
        } finally {
            frame.release();
        }
    }

}

package com.example.sdk.core.util;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;

/**
 * 自适应 ServerBootstrap 工厂
 * 自动检测环境是否支持 Epoll
 */
@Slf4j
public class ServerBootstrapFactory {

    /**
     * 根据当前环境（是否支持 Epoll）创建合适的 EventLoopGroup。
     * 推荐在 Linux 生产环境下使用 Epoll 以获得更高性能。
     *
     * @param threads          线程数，0 表示使用 Netty 默认值 (CPU 核心数 * 2)
     * @param threadNamePrefix 线程名称前缀，便于排查问题
     * @return EventLoopGroup (NioEventLoopGroup 或 EpollEventLoopGroup)
     */
    public static EventLoopGroup newEventLoopGroup(int threads, String threadNamePrefix) {
        ThreadFactory threadFactory = new NamedThreadFactory(threadNamePrefix);
        if (Epoll.isAvailable()) {
            log.info("【高性能模式】检测到 Epoll 可用，正在为 [{}] 使用 EpollEventLoopGroup...", threadNamePrefix);
            return new EpollEventLoopGroup(threads, threadFactory);
        } else {
            log.info("【通用模式】Epoll 不可用 (原因: {})，正在为 [{}] 使用 NioEventLoopGroup...", Epoll.unavailabilityCause(), threadNamePrefix);
            return new NioEventLoopGroup(threads, threadFactory);
        }
    }

    /**
     * 获取与 EventLoopGroup 匹配的 ServerSocketChannel 类。
     * 如果使用了 EpollEventLoopGroup，必须使用 EpollServerSocketChannel。
     *
     * @return ServerSocketChannel.class
     */
    public static Class<? extends ServerSocketChannel> getServerSocketChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        } else {
            return NioServerSocketChannel.class;
        }
    }

    public static Class<? extends SocketChannel> getSocketChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        } else {
            return NioSocketChannel.class;
        }
    }

}
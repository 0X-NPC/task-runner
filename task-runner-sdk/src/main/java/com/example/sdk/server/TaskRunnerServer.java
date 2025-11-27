package com.example.sdk.server;

import com.example.sdk.core.TaskRunner;
import com.example.sdk.core.codec.NettyDecoder;
import com.example.sdk.core.codec.SerializerType;
import com.example.sdk.core.codec.ZeroCopyNettyEncoder;
import com.example.sdk.core.command.CommandType;
import com.example.sdk.core.command.RemotingCommand;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task Runner Server 核心实现
 *
 * @author 0xNPC
 */
@Slf4j
public class TaskRunnerServer {

    private final int port;
    private final TaskProvider taskProvider;
    private final TaskResultListener resultListener;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final ThreadPoolExecutor businessExecutor;

    // --- Worker 管理与同步调用支持 ---
    /**
     * WorkerId -> Channel 映射
     */
    private final ConcurrentHashMap<String, Channel> workerChannelMap = new ConcurrentHashMap<>();
    /**
     * RequestId -> Future (用于 Server 同步调用 Worker 等待结果)
     */
    private final ConcurrentHashMap<Long, CompletableFuture<RemotingCommand>> pendingResponses = new ConcurrentHashMap<>();

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile boolean isStopping = false;
    private final CountDownLatch waitLatch = new CountDownLatch(1);

    public TaskRunnerServer(int port, TaskProvider taskProvider, TaskResultListener resultListener) {
        this.port = port;
        this.taskProvider = taskProvider;
        this.resultListener = resultListener;
        this.businessExecutor = new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors() * 2, 200, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10000), new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    // --- Server 主动 Push 接口 ---

    /**
     * 同步发送命令给指定 Worker
     *
     * @param workerId       目标 Worker ID
     * @param body           请求数据
     * @param timeoutMillis  超时时间
     * @param serializerType 序列化方式
     * @return Worker 的响应数据
     */
    public RemotingCommand sendSync(String workerId, Object body, long timeoutMillis, SerializerType serializerType) throws TimeoutException {
        Channel channel = workerChannelMap.get(workerId);
        if (channel == null || !channel.isActive()) {
            throw new RuntimeException("Worker [" + workerId + "] is offline or not registered.");
        }

        RemotingCommand request = RemotingCommand.createRequest(CommandType.SERVER_PUSH_REQ, body);
        request.setSerializerType(serializerType); // 设置指定的序列化方式

        CompletableFuture<RemotingCommand> future = new CompletableFuture<>();
        pendingResponses.put(request.getRequestId(), future);

        try {
            channel.writeAndFlush(request).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    pendingResponses.remove(request.getRequestId());
                    future.completeExceptionally(f.cause());
                }
            });

            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Execution failed", e);
        } finally {
            pendingResponses.remove(request.getRequestId());
        }
    }

    /**
     * 非阻塞启动
     * <p>
     * 说明：适用于嵌入式集成场景，比如当SDK被集成到Spring Boot或其他应用容器中时
     */
    public void start() {
        if (!isRunning.compareAndSet(false, true)) return;
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Server-Shutdown-Hook"));

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new IdleStateHandler(30, 0, 0))
                                .addLast(new NettyDecoder())
                                .addLast(new ZeroCopyNettyEncoder())
                                .addLast(new ServerHandler());
                    }
                });

        try {
            b.bind(port).sync();
            log.info("Task Server started on port: {}", port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 阻塞启动，独立进程运行
     * <p>
     * 阻塞当前线程，直到服务停止（stop() 被调用或收到停机信号）才会解除阻塞。
     */
    public void startAndWait() {
        start();
        try {
            waitLatch.await();
        } catch (InterruptedException ignore) {
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("Stopping Server... Entering DRAIN mode.");
            isStopping = true;
            businessExecutor.shutdown();
            if (bossGroup != null) bossGroup.shutdownGracefully();
            if (workerGroup != null) workerGroup.shutdownGracefully();
            waitLatch.countDown();
        }
    }

    @ChannelHandler.Sharable
    private class ServerHandler extends SimpleChannelInboundHandler<RemotingCommand> {

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // 连接断开时，虽然 Map 里的 Channel 可能已经失效，但为了保险可以遍历清理，
            // 或者在 get 时判断 isActive。简单起见，此处不做昂贵的遍历清理，依靠 get 时的校验。
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                if (((IdleStateEvent) evt).state() == IdleState.READER_IDLE) {
                    log.warn("Client {} idle timeout, closing.", ctx.channel().remoteAddress());
                    ctx.close();
                }
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RemotingCommand msg) {
            // 如果是 Worker 对 Server Push 的响应
            if (msg.getType() == CommandType.SERVER_PUSH_RESP) {
                CompletableFuture<RemotingCommand> future = pendingResponses.remove(msg.getRequestId());
                if (future != null) {
                    future.complete(msg);
                }
                return;
            }

            // 处理 Worker 的请求
            try {
                businessExecutor.submit(() -> processWorkerRequest(ctx, msg));
            } catch (RejectedExecutionException e) {
                log.error("Server busy, dropping request");
            }
        }

        private void processWorkerRequest(ChannelHandlerContext ctx, RemotingCommand msg) {
            if (isStopping && msg.getType() == CommandType.PULL_TASK_REQ) {
                ctx.writeAndFlush(RemotingCommand.createResponse(msg.getRequestId(), CommandType.PULL_TASK_RESP, null));
                return;
            }

            switch (msg.getType()) {
                case PULL_TASK_REQ:
                    handlePullAsync(ctx, msg);
                    break;
                case REPORT_RESULT_REQ:
                    resultListener.onResult(msg);
                    ctx.writeAndFlush(RemotingCommand.createResponse(msg.getRequestId(), CommandType.REPORT_RESULT_RESP, "ACK"));
                    break;
                case PING:
                    ctx.writeAndFlush(RemotingCommand.createResponse(msg.getRequestId(), CommandType.PONG, null));
                    break;
            }
        }

        private void handlePullAsync(ChannelHandlerContext ctx, RemotingCommand msg) {
            // 核心逻辑：借用 Pull 机会注册 Worker Channel
            String workerId = TaskRunner.decodeBody(msg, String.class);
            if (workerId != null) {
                workerChannelMap.put(workerId, ctx.channel());
            }

            CompletableFuture<Object> future = taskProvider.pullTask(workerId);
            future.thenAccept(task -> {
                if (ctx.channel().isActive()) {
                    RemotingCommand resp = RemotingCommand.createResponse(msg.getRequestId(), CommandType.PULL_TASK_RESP, task);
                    // 保持和请求相同的序列化方式
                    resp.setSerializerType(msg.getSerializerType());
                    ctx.writeAndFlush(resp);
                }
            }).exceptionally(ex -> {
                log.error("Pull error", ex);
                return null;
            });
        }
    }
}
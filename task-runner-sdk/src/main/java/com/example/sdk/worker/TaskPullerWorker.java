package com.example.sdk.worker;

import com.example.sdk.core.codec.NettyDecoder;
import com.example.sdk.core.codec.NettyEncoder;
import com.example.sdk.core.codec.SerializerType;
import com.example.sdk.core.command.CommandType;
import com.example.sdk.core.command.RemotingCommand;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task Puller Worker 核心实现
 *
 * @author 0xNPC
 */
@Slf4j
public class TaskPullerWorker {

    private final String serverHost;
    private final int serverPort;
    private final String workerId;
    private final TaskExecutor taskExecutor;

    // --- Server Push 处理器 ---
    @Setter
    private ServerRequestProcessor serverRequestProcessor;

    private EventLoopGroup group;
    private volatile Channel channel;
    private final ConcurrentHashMap<Long, CompletableFuture<RemotingCommand>> pendingRequests = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService pullExecutor = Executors.newSingleThreadExecutor();

    // 专门处理 Server Push 指令的线程池，避免和任务执行争抢
    private final ExecutorService systemExecutor = Executors.newCachedThreadPool();
    private final ThreadPoolExecutor taskWorkerPool;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final CountDownLatch waitLatch = new CountDownLatch(1);

    public TaskPullerWorker(String serverHost, int serverPort, String workerId, TaskExecutor taskExecutor) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.workerId = workerId;
        this.taskExecutor = taskExecutor;
        this.taskWorkerPool = new ThreadPoolExecutor(4, 16, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100));
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 非阻塞启动
     * <p>
     * 说明：适用于嵌入式集成场景，比如当SDK被集成到Spring Boot或其他应用容器中时
     */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Worker-Shutdown"));
            group = new NioEventLoopGroup();
            doConnect(0);
            startPullLoop();
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("Stopping Worker...");
            pullExecutor.shutdownNow();
            systemExecutor.shutdownNow();
            scheduler.shutdownNow();
            if (group != null) group.shutdownGracefully();
            taskWorkerPool.shutdown();
            waitLatch.countDown();
        }
    }

    private void doConnect(int retryCount) {
        if (!isRunning.get()) return;

        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new IdleStateHandler(0, 10, 0))
                                .addLast(new NettyDecoder())
                                .addLast(new NettyEncoder())
                                .addLast(new ClientHandler());
                    }
                });

        b.connect(serverHost, serverPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("Connected to server {}:{}", serverHost, serverPort);
                this.channel = future.channel();
            } else {
                long delay = calculateDelay(retryCount);
                scheduler.schedule(() -> doConnect(retryCount + 1), delay, TimeUnit.MILLISECONDS);
            }
        });
    }

    private long calculateDelay(int retry) {
        long delay = 1000L * (1 << Math.min(retry, 5));
        return Math.min(delay, 30000);
    }

    private void startPullLoop() {
        pullExecutor.submit(() -> {
            while (isRunning.get()) {
                try {
                    if (taskWorkerPool.getQueue().size() > 50) {
                        Thread.sleep(100);
                        continue;
                    }
                    if (channel == null || !channel.isActive()) {
                        Thread.sleep(1000);
                        continue;
                    }

                    RemotingCommand request = RemotingCommand.createRequest(CommandType.PULL_TASK_REQ, workerId);
                    // 默认使用 Protostuff，也可以配置
                    request.setSerializerType(SerializerType.PROTOSTUFF);

                    CompletableFuture<RemotingCommand> future = new CompletableFuture<>();
                    pendingRequests.put(request.getRequestId(), future);

                    channel.writeAndFlush(request);

                    try {
                        RemotingCommand response = future.get(10, TimeUnit.SECONDS);
                        byte[] cachedBody = response.getCachedBodyBytes();
                        if (cachedBody != null && cachedBody.length > 0) {
                            taskWorkerPool.submit(() -> executeTask(cachedBody, response.getSerializerType()));
                        } else {
                            Thread.sleep(1000);
                        }
                    } catch (TimeoutException e) {
                        pendingRequests.remove(request.getRequestId());
                    }
                } catch (Exception e) {
                    try {
                        Thread.sleep(2000);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void executeTask(byte[] taskData, SerializerType serializerType) {
        try {
            // 此处把 serializerType 传给 TaskExecutor 让他决定怎么反序列化
            // 或者简单起见，Worker 统一约定
            Object result = taskExecutor.execute(taskData);
            if (channel != null && channel.isActive()) {
                RemotingCommand resp = RemotingCommand.createRequest(CommandType.REPORT_RESULT_REQ, result);
                resp.setSerializerType(serializerType); // 保持一致
                channel.writeAndFlush(resp);
            }
        } catch (Exception e) {
            log.error("Task failed", e);
        }
    }

    public interface TaskExecutor {
        Object execute(byte[] taskData);
    }

    private class ClientHandler extends SimpleChannelInboundHandler<RemotingCommand> {
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.warn("Connection lost, reconnecting...");
            channel = null;
            scheduler.schedule(() -> doConnect(0), 1, TimeUnit.SECONDS);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(RemotingCommand.createRequest(CommandType.PING, null));
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RemotingCommand msg) {
            // 1. 处理 Server 的 PUSH 请求
            if (msg.getType() == CommandType.SERVER_PUSH_REQ) {
                handleServerPush(ctx, msg);
                return;
            }

            // 2. 处理自己发出的请求的响应
            if (msg.getType() == CommandType.PONG) return;
            CompletableFuture<RemotingCommand> future = pendingRequests.remove(msg.getRequestId());
            if (future != null) {
                future.complete(msg);
            }
        }

        private void handleServerPush(ChannelHandlerContext ctx, RemotingCommand request) {
            systemExecutor.submit(() -> {
                Object result = null;
                try {
                    if (serverRequestProcessor != null) {
                        result = serverRequestProcessor.process(request);
                    } else {
                        log.warn("No processor for Server Push");
                    }
                } catch (Exception e) {
                    result = "ERROR: " + e.getMessage();
                }

                // 返回响应
                RemotingCommand response = RemotingCommand.createResponse(
                        request.getRequestId(),
                        CommandType.SERVER_PUSH_RESP,
                        result
                );
                // 保持序列化协议一致
                response.setSerializerType(request.getSerializerType());
                ctx.writeAndFlush(response);
            });
        }
    }
}
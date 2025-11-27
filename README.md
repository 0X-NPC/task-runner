# 项目说明
基于 Netty 开发的高性能、轻量级分布式任务调度通信 SDK，提供稳定的底层通信能力。

# 🌟 核心特性
## 1. 双模通信架构
支持**全双工通信**，融合数据与控制面需求：
- 数据面 - Pull（拉取）：Worker 主动拉取任务，天然实现负载均衡与 Server 无状态化，适合高吞吐分发场景。
- 控制面 - Push（推送）：Server 可通过 WorkerID 定向发送控制指令（如终止任务），支持阻塞等待结果的同步调用。

## 2. 双序列化协议支持
支持连接内混合使用多种协议，灵活切换：
- Protostuff (默认)：高效二进制序列化，体积小、编解码快，适合高频任务传输。
- JSON (Jackson)：通用文本序列化，便于调试与运维，适合控制指令。

## 3. Netty 高性能底座
- 基于 Netty 4.x NIO 与 Reactor 多线程模型。
- 零拷贝 (Zero-Copy)：编解码层直接操作 ByteBuf，减少内存拷贝。
- 全链路异步 I/O：业务处理返回 CompletableFuture，杜绝阻塞 Netty I/O 线程。


## 4. 高可用与稳定性
- 自定义私有协议：通过自定义协议头完美解决 TCP 粘包/拆包问题。
- 智能保活：双向心跳检测（Worker 10s PING / Server 30s 读空闲断连）。
- 断线重连：Worker 采用指数退避算法 (Exponential Backoff)，防止重连风暴。
- 背压机制 (Backpressure)：本地队列积压时主动暂停拉取，防止 OOM。
- 优雅停机：支持 Drain Mode，拒绝新任务并确保在途请求处理完毕。

# 🚀 快速开始 (Quick Start)
**Server 端开发**

Server 端既是任务生产者（Pull），也是指令发起者（Push）。
```java
// 1. 创建 Server
TaskRunnerServer server = new TaskRunnerServer(8888,
    // [Pull] 异步返回任务
    (workerId) -> CompletableFuture.supplyAsync(() -> taskQueue.poll()),
    // [Result] 监听汇报
    (resultCmd) -> log.info("Task Result: {}", resultCmd)
);

// 2. 启动
server.startAndWait();

// 3. [Push] 同步发送控制指令 (3s 超时)
RemotingCommand response = server.sendSync(
    "Worker-001", 
    "CMD_REFRESH_CACHE", 
    3000, 
    SerializerType.JSON // 指定 JSON 方便调试
);
```

**Worker 端开发**

Worker 端既是任务执行者，也可处理控制指令。
```java
// 1. 创建 Worker
TaskRunnerWorker worker = new TaskRunnerWorker("127.0.0.1", 8888, "Worker-001",
    // [Pull] 执行核心任务
    (taskBytes) -> {
        // ... 业务逻辑 ...
        return new TaskResult("Success");
    }
);

// 2. [Push] 注册指令处理器
worker.setServerRequestProcessor(request -> {
    String cmd = TaskPuller.decodeBody(request, String.class);
    if ("CMD_REFRESH_CACHE".equals(cmd)) {
        return "OK";
    }
    return "UNKNOWN";
});

// 3. 启动
worker.startAndWait();

```
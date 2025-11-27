package com.example.demo;

import com.example.demo.model.DemoTask;
import com.example.demo.model.DemoTaskResult;
import com.example.sdk.core.TaskPuller;
import com.example.sdk.core.codec.SerializerType;
import com.example.sdk.core.command.RemotingCommand;
import com.example.sdk.server.TaskPullerServer;
import lombok.extern.slf4j.Slf4j;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DemoServer {

    public static void main(String[] args) {
        // 1. 模拟任务存储 (Pull 模式的数据源)
        Queue<DemoTask> taskQueue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 100; i++) {
            taskQueue.add(new DemoTask("TASK-" + i, "echo 'Hello " + i + "'", System.currentTimeMillis()));
        }

        // 2. 创建 Server
        TaskPullerServer server = new TaskPullerServer(8888,
                // [Pull模式] TaskProvider: 响应 Worker 的拉取请求
                (workerId) -> CompletableFuture.supplyAsync(() -> {
                    // 模拟查库耗时
                    try {
                        TimeUnit.MILLISECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }

                    DemoTask task = taskQueue.poll();
                    if (task != null) {
                        log.info("[Pull] Assigned {} to {}", task.taskId(), workerId);
                    }
                    return task;
                }),

                // [Pull模式] ResultListener: 处理任务结果
                (resultCmd) -> {
                    DemoTaskResult result = TaskPuller.decodeBody(resultCmd, DemoTaskResult.class);
                    if (result != null) {
                        log.debug("[Result] ID={}, Success={}", result.taskId(), result.success());
                    }
                }
        );

        // 3. 启动 Server
        server.start();
        log.info("DemoServer started on port 8888.");

        // 4. [Push模式] 模拟 Server 主动下发指令 (控制平面)
        // 开启一个线程，每隔 5 秒向 "Worker-Test" 发送一个 JSON 指令
        new Thread(() -> {
            String targetWorker = "Worker-Test";
            while (true) {
                try {
                    Thread.sleep(5000);
                    log.info(">>> [Push] Sending command to {}", targetWorker);

                    // 主动发送同步命令 (使用 JSON 序列化方便调试)
                    String commandPayload = "CMD_REFRESH_CACHE: " + System.currentTimeMillis();

                    try {
                        // 记录开始时间
                        long start = System.currentTimeMillis();

                        RemotingCommand response = server.sendSync(
                                targetWorker,
                                commandPayload,
                                3000,
                                SerializerType.JSON // 指定使用 JSON
                        );

                        // 计算耗时
                        long cost = System.currentTimeMillis() - start;

                        // 解析响应
                        String respStr = TaskPuller.decodeBody(response, String.class);
                        log.info("<<< [Push] Received response from {}: {} | cost: {} ms", targetWorker, respStr, cost);

                    } catch (Exception e) {
                        log.warn("[Push] Failed to push to {}: {}", targetWorker, e.getMessage());
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();

        // 阻塞主线程
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
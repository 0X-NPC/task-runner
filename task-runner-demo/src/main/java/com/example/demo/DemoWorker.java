package com.example.demo;

import com.example.demo.model.DemoTask;
import com.example.demo.model.DemoTaskResult;
import com.example.sdk.core.TaskPuller;
import com.example.sdk.worker.TaskPullerWorker;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

@Slf4j
public class DemoWorker {

    public static void main(String[] args) {
        // 为了方便 Server 推送，我们固定一个 ID，或者通过启动参数指定
        String workerId = args.length > 0 ? args[0] : "Worker-Test";

        TaskPullerWorker worker = new TaskPullerWorker("127.0.0.1", 8888, workerId,
                // 1. [Pull模式] TaskExecutor: 处理拉取到的任务
                (taskBytes) -> {
                    try {
                        // 使用辅助类自动根据协议头反序列化 (支持 Protostuff/JSON)
                        // 注意：这里需要构造一个临时的 RemotingCommand 容器或者直接使用具体的 Serializer
                        // 在 Demo 简单场景下，我们假设 Pull 的任务默认是 Protostuff
                        // 更好的方式是 SDK 传递 body 时带上 serializerType，或者这里直接用工具类
                        DemoTask task = com.example.sdk.core.Constants.PROTO_SERIALIZER.deserialize(taskBytes, DemoTask.class);

                        log.info("--- [Task] Executing: {} ---", task.script());
                        Thread.sleep(new Random().nextInt(200)); // 模拟执行

                        return new DemoTaskResult(task.taskId(), true, "Done");
                    } catch (Exception e) {
                        return new DemoTaskResult("UNKNOWN", false, e.getMessage());
                    }
                }
        );

        // 2. [Push模式] 注册 ServerRequestProcessor: 处理 Server 下发的指令
        worker.setServerRequestProcessor(request -> {
            // 自动根据 Server 发送时的序列化类型 (JSON) 进行解码
            String cmd = TaskPuller.decodeBody(request, String.class);

            log.info("!!! [Push] Received Server Command: {} !!!", cmd);

            // 执行控制逻辑 (例如刷新缓存、修改日志级别等)
            if (cmd != null && cmd.startsWith("CMD_REFRESH_CACHE")) {
                // do refresh...
                return "CACHE_REFRESHED_SUCCESS";
            }

            return "UNKNOWN_COMMAND";
        });

        log.info("Starting Worker: {}...", workerId);
        worker.startAndWait();
    }
}
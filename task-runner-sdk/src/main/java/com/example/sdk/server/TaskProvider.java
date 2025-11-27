package com.example.sdk.server;

import java.util.concurrent.CompletableFuture;

/**
 * 任务提取器
 * <p>
 * 异步接口， 返回 Future，真正实现NIO
 *
 * @author 0xNPC
 */
public interface TaskProvider {

    CompletableFuture<Object> pullTask(String workerId);

}

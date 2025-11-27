package com.example.sdk.worker;

import com.example.sdk.core.command.RemotingCommand;

/**
 * Worker 端处理 Server Push 请求的处理器
 *
 * @author 0xNPC
 */
public interface ServerRequestProcessor {

    /**
     * 处理 Server 发来的请求
     *
     * @param request Server 的请求命令
     * @return 业务处理结果 (将被回传给 Server)
     */
    Object process(RemotingCommand request);

}
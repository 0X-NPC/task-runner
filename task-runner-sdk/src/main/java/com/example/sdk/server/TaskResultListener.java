package com.example.sdk.server;

import com.example.sdk.core.command.RemotingCommand;

/**
 * 任务结果监听接口
 *
 * @author 0xNPC
 */
public interface TaskResultListener {

    void onResult(RemotingCommand resultCmd);

}

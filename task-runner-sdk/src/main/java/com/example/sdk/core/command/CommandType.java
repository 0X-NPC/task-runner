package com.example.sdk.core.command;

/**
 * 命令类型
 *
 * @author 0xNPC
 */
public enum CommandType {

    PING, PONG,

    /**
     * Worker -> Server (Pull模式)
     */
    PULL_TASK_REQ, PULL_TASK_RESP,
    REPORT_RESULT_REQ, REPORT_RESULT_RESP,

    /**
     * Server -> Worker (Push模式)
     */
    SERVER_PUSH_REQ, SERVER_PUSH_RESP
}
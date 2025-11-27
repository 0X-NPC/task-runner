package com.example.sdk.core.command;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求ID生成器
 *
 * @author 0xNPC
 */
public class RequestIdGenerator {

    /**
     * 使用 System.nanoTime() 作为种子，既保证了单机递增，又极大降低了重启后 ID 重复的概率
     */
    private static final AtomicLong ATOMIC_LONG = new AtomicLong(System.currentTimeMillis());

    public static long nextId() {
        return ATOMIC_LONG.incrementAndGet();
    }

}

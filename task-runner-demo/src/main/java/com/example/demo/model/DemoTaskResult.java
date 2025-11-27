package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 业务数据模型-任务结果
 *
 * @author zhiwu.zzw
 */
@Data
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class DemoTaskResult {
    private String taskId;
    private boolean success;
    private String output;
}

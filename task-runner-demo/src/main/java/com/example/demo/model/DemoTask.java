package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 业务数据模型-任务
 *
 * @author zhiwu.zzw
 */
@Data
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class DemoTask {
    private String taskId;
    private String script;
    private long timestamp;
}

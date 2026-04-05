package com.ling.linginnerflow.memory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条对话消息
 * 存在Redis里，组成对话历史列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {

    // "user" 或 "assistant"
    private String role;

    // 消息内容
    private String content;

    // 时间戳
    private long timestamp;
}
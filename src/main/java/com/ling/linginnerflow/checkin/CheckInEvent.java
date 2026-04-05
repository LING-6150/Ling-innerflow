package com.ling.linginnerflow.checkin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 打卡事件消息体
 * 通过Kafka传递，Consumer异步处理
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckInEvent {
    private Long checkInId;
    private String userId;
    private String content;
}
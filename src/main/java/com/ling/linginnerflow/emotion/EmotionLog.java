package com.ling.linginnerflow.emotion;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 情绪日志实体
 * 每次对话自动记录，用于情绪趋势分析
 */
@Data
@Entity
@Table(name = "emotion_log")
public class EmotionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    // 情绪等级 L1-L5
    @Column(nullable = false)
    private Integer emotionLevel;

    // 用户输入内容
    @Column(length = 1000)
    private String userInput;

    // AI回复
    @Column(length = 2000)
    private String aiResponse;

    // 来源：chat=对话，checkin=打卡
    @Column(nullable = false)
    private String source;

    // 记录时间
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
package com.ling.linginnerflow.memory;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户长期记忆实体
 * 存MySQL，跨会话持久化
 * 记录用户的情绪模式、核心困扰、应对偏好
 */
@Data
@Entity
@Table(name = "user_memory")
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    // 情绪模式摘要，如"容易焦虑，常见认知扭曲：全或无思维"
    @Column(length = 1000)
    private String emotionPattern;

    // 核心困扰，如"工作压力、人际关系"
    @Column(length = 500)
    private String coreStruggles;

    // 有效的应对方式，如"用户对呼吸练习反应好"
    @Column(length = 500)
    private String effectiveCoping;

    // 对话摘要（最近一次）
    @Column(length = 2000)
    private String conversationSummary;

    // 最后更新时间
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
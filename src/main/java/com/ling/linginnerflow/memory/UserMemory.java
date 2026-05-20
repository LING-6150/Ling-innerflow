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

    // 已知情绪触发点，JSON数组，每条带count/firstSeen/lastSeen
    @Column(columnDefinition = "TEXT")
    private String triggers;

    // 治疗进展记录，JSON数组，每条带date/note
    @Column(columnDefinition = "TEXT")
    private String progressNotes;

    // Wiki变更日志，记录每次编译更新了什么
    @Column(columnDefinition = "TEXT")
    private String wikiChangeLog;

    // 用户主动纠错记录，JSON数组，作为审计追踪
    @Column(columnDefinition = "TEXT")
    private String userCorrections;

    // 用户表达内心状态的语言风格，如"常用身体隐喻，倾向于轻描淡写"
    @Column(length = 300)
    private String languageStyle;

    // 对话摘要（最近一次压缩产生的）
    @Column(length = 2000)
    private String conversationSummary;

    // 累计压缩次数，反映对话深度
    @Column(columnDefinition = "int default 0")
    private int compressionCount = 0;

    // 在 updatedAt 字段前面加
// 陪伴人格偏好：WARM / QUIET / RATIONAL，默认WARM
    @Column(length = 20)
    private String persona = "WARM";

    // 最后更新时间
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String reflection;

    // AI-detected contradictions between new observations and existing wiki, JSON array
    @Column(columnDefinition = "TEXT")
    private String conflicts;

    private LocalDateTime lastActiveAt;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
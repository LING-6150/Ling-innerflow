package com.ling.linginnerflow.checkin;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 打卡记录实体
 * 对应数据库表 check_in
 * JPA会根据这个类自动建表（ddl-auto=update）
 */
@Data
@Entity
@Table(name = "check_in")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 用户ID，暂时用字符串，后续接入用户系统
    @Column(nullable = false)
    private String userId;

    // 用户输入的情绪内容
    @Column(nullable = false, length = 1000)
    private String content;

    // 情绪等级 L1-L5
    @Column(nullable = false)
    private Integer emotionLevel;

    // AI生成的回复
    @Column(length = 2000)
    private String aiResponse;

    // public=公开，private=私密
    @Column(nullable = false)
    private String visibility; // "public" or "private"

    // 打卡时间
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // CheckIn.java 加一个字段
    @Column(nullable = false)
    private Boolean needAI = true;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
package com.ling.linginnerflow.checkin;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 打卡共鸣表
 * 用户对公开打卡的轻量互动
 */
@Data
@Entity
@Table(name = "checkin_reaction",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"check_in_id", "user_id"}))
public class CheckInReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long checkInId;

    @Column(nullable = false)
    private String userId;

    // 类型：hug=抱抱
    @Column(nullable = false)
    private String type = "hug";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
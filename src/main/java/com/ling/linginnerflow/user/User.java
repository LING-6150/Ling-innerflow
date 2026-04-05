package com.ling.linginnerflow.user;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 * 对应数据库表 user
 */
@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 用户名，唯一
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // 邮箱，唯一
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // 加密后的密码
    @Column(nullable = false)
    private String password;

    // 用户角色：USER / ADMIN
    @Column(nullable = false)
    private String role = "USER";

    // 账号是否启用
    @Column(nullable = false)
    private boolean enabled = true;

    // 注册时间
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
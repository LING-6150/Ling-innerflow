// src/main/java/com/ling/linginnerflow/websocket/ChatMessage.java
package com.ling.linginnerflow.websocket;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_message")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String role; // "user" 或 "assistant"

    @Column(length = 5000)
    private String content;

    private Integer emotionLevel; // 只有user消息有

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
// src/main/java/com/ling/linginnerflow/image/EmotionImage.java
package com.ling.linginnerflow.image;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "emotion_image")
public class EmotionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private Integer emotionLevel;

    // 存Base64图片数据
    @Column(columnDefinition = "LONGTEXT")
    private String imageBase64;

    // 生成图片时的情绪摘要
    @Column(length = 500)
    private String emotionSummary;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
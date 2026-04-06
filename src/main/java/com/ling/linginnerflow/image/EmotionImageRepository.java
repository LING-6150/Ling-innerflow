// src/main/java/com/ling/linginnerflow/image/EmotionImageRepository.java
package com.ling.linginnerflow.image;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmotionImageRepository extends JpaRepository<EmotionImage, Long> {
    // 查用户最新一张
    Optional<EmotionImage> findTopByUserIdOrderByCreatedAtDesc(String userId);
    // 查用户最近5张
    List<EmotionImage> findTop5ByUserIdOrderByCreatedAtDesc(String userId);
}
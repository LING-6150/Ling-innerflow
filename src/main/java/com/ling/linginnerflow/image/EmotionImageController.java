// src/main/java/com/ling/linginnerflow/image/EmotionImageController.java
package com.ling.linginnerflow.image;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emotion-image")
@RequiredArgsConstructor
public class EmotionImageController {

    private final EmotionImageService emotionImageService;

    // 查最新画像
    @GetMapping("/latest")
    public Map<String, Object> getLatest() {
        String userId = getUserIdFromToken();
        return emotionImageService.getLatestImage(userId)
                .map(img -> Map.<String, Object>of(
                        "id", img.getId(),
                        "imageBase64", img.getImageBase64(),
                        "emotionLevel", img.getEmotionLevel(),
                        "createdAt", img.getCreatedAt().toString()
                ))
                .orElse(Map.of("message", "暂无画像"));
    }

    // 查最近5张
    @GetMapping("/recent")
    public List<Map<String, Object>> getRecent() {
        String userId = getUserIdFromToken();
        return emotionImageService.getRecentImages(userId).stream()
                .map(img -> Map.<String, Object>of(
                        "id", img.getId(),
                        "imageBase64", img.getImageBase64(),
                        "emotionLevel", img.getEmotionLevel(),
                        "createdAt", img.getCreatedAt().toString()
                ))
                .toList();
    }

    private String getUserIdFromToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (String) auth.getPrincipal();
    }
}
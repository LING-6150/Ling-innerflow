// src/main/java/com/ling/linginnerflow/image/EmotionImageService.java
package com.ling.linginnerflow.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionImageService {

    private final EmotionImageRepository emotionImageRepository;
    private final RestTemplate restTemplate;

    @Value("${spring.ai.openai.api-key}")
    private String openaiApiKey;

    // 每个情绪等级对应的画像风格
    private String buildPrompt(int level, String userInput) {
        String style = switch (level) {
            case 1 -> "warm and peaceful watercolor landscape, soft golden light, gentle rolling hills, calm and serene";
            case 2 -> "calm blue watercolor abstract, soft flowing waves, quiet misty atmosphere, gentle and soothing";
            case 3 -> "deep indigo and violet watercolor, stormy clouds with light breaking through, complex emotions but hopeful";
            case 4 -> "dark abstract expressionism, heavy muted purples and grays, raw emotion, one small warm glow in corner";
            case 5 -> "minimal dark composition, single small light in vast darkness, simple, quiet, not hopeless";
            default -> "soft pastel watercolor abstract, gentle colors, peaceful";
        };
        return "Abstract emotional artwork, " + style +
                ", no people, no text, no faces, painterly style, high quality";
    }

    // 生成情绪画像（对话结束时调用，在受管线程池上异步执行）
    @Async("emotionImageExecutor")
    public EmotionImage generateImage(String userId, int emotionLevel,
                                      String userInput) {
        try {
            log.info("开始生成情绪画像: userId={}, level={}", userId, emotionLevel);

            String prompt = buildPrompt(emotionLevel, userInput);

            // 调DALL-E 3 API
            Map<String, Object> requestBody = Map.of(
                    "model", "dall-e-3",
                    "prompt", prompt,
                    "n", 1,
                    "size", "1024x1024",
                    "quality", "standard"
            );

            var headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + openaiApiKey);
            headers.set("Content-Type", "application/json");

            var request = new org.springframework.http.HttpEntity<>(requestBody, headers);
            var response = restTemplate.postForObject(
                    "https://api.openai.com/v1/images/generations",
                    request,
                    Map.class
            );

            // 取URL
            var data = (List<Map<String, String>>) response.get("data");
            String imageUrl = data.get(0).get("url");

            // 下载图片转Base64
            String base64 = downloadAndEncode(imageUrl);

            // 存数据库
            EmotionImage image = new EmotionImage();
            image.setUserId(userId);
            image.setEmotionLevel(emotionLevel);
            image.setImageBase64(base64);
            image.setEmotionSummary(prompt);
            EmotionImage saved = emotionImageRepository.save(image);

            log.info("情绪画像生成成功: userId={}", userId);
            return saved;

        } catch (Exception e) {
            log.error("情绪画像生成失败: {}", e.getMessage());
            return null;
        }
    }

    // 下载图片并转Base64
    private String downloadAndEncode(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        // try-with-resources 确保 InputStream 关闭，避免连接池泄漏
        try (java.io.InputStream in = url.openStream()) {
            byte[] imageBytes = in.readAllBytes();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }

    // 查询最新画像
    public Optional<EmotionImage> getLatestImage(String userId) {
        return emotionImageRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
    }

    // 查询最近5张
    public List<EmotionImage> getRecentImages(String userId) {
        return emotionImageRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId);
    }
}
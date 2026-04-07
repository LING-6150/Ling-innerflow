// src/main/java/com/ling/linginnerflow/multimodal/EmotionFusionService.java
package com.ling.linginnerflow.multimodal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionFusionService {

    private final RestTemplate restTemplate;

    private static final String PYTHON_SERVICE_URL =
            "http://127.0.0.1:5001/analyze";

    // ===== 语音情绪分析 =====
    public int analyzeVoiceEmotion(byte[] wavBytes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource audioResource =
                    new ByteArrayResource(wavBytes) {
                        @Override
                        public String getFilename() { return "audio.wav"; }
                    };

            MultiValueMap<String, Object> body =
                    new LinkedMultiValueMap<>();
            body.add("audio", audioResource);

            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    PYTHON_SERVICE_URL, request, Map.class);

            int voiceLevel = (int) response.getBody()
                    .getOrDefault("emotionLevel", 2);
            log.info("语音情绪分析结果: L{}", voiceLevel);
            return voiceLevel;

        } catch (Exception e) {
            log.warn("语音情绪分析失败，降级: {}", e.getMessage());
            return -1; // -1表示分析失败，不参与融合
        }
    }

    // ===== 多模态融合（注意力加权）=====
    // 三模态融合
    public int fuseEmotions(int textLevel, int voiceLevel, int imageLevel) {
        double weightedSum = 0;
        double totalWeight = 0;

        // 文字权重最高
        weightedSum += textLevel * 0.5;
        totalWeight += 0.5;

        if (voiceLevel != -1) {
            weightedSum += voiceLevel * 0.3;
            totalWeight += 0.3;
        }

        if (imageLevel != -1) {
            weightedSum += imageLevel * 0.2;
            totalWeight += 0.2;
        }

        int fusedLevel = (int) Math.round(weightedSum / totalWeight);
        fusedLevel = Math.max(1, Math.min(5, fusedLevel));

        log.info("三模态融合: 文字L{} + 语音L{} + 图片L{} → 融合L{}",
                textLevel, voiceLevel, imageLevel, fusedLevel);

        return fusedLevel;
    }


    // 图片情绪分析
    public int analyzeImageEmotion(byte[] imageBytes, String contentType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource imageResource =
                    new ByteArrayResource(imageBytes) {
                        @Override
                        public String getFilename() { return "image.jpg"; }
                    };

            MultiValueMap<String, Object> body =
                    new LinkedMultiValueMap<>();
            body.add("image", imageResource);

            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://127.0.0.1:5001/analyze-image",
                    request, Map.class);

            int imageLevel = (int) response.getBody()
                    .getOrDefault("emotionLevel", -1);
            log.info("图片情绪分析结果: L{}", imageLevel);
            return imageLevel;

        } catch (Exception e) {
            log.warn("图片情绪分析失败: {}", e.getMessage());
            return -1;
        }
    }
}
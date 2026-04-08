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
    private static final String PYTHON_SERVICE_URL = "http://127.0.0.1:5001/analyze";

    // ===== 语音情绪分析（返回level+confidence）=====
    public EmotionAnalysisResult analyzeVoiceEmotion(byte[] wavBytes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource audioResource = new ByteArrayResource(wavBytes) {
                @Override public String getFilename() { return "audio.wav"; }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("audio", audioResource);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    PYTHON_SERVICE_URL,
                    new HttpEntity<>(body, headers), Map.class);

            int level = ((Number) response.getBody()
                    .getOrDefault("emotionLevel", 2)).intValue();
            double confidence = ((Number) response.getBody()
                    .getOrDefault("probability", 0.5)).doubleValue();

            log.info("语音情绪分析: L{}, confidence={}", level, confidence);
            return new EmotionAnalysisResult(level, confidence);

        } catch (Exception e) {
            log.warn("语音情绪分析失败: {}", e.getMessage());
            return new EmotionAnalysisResult(-1, 0.0);
        }
    }

    // ===== 图片情绪分析（返回level+confidence）=====
    public EmotionAnalysisResult analyzeImageEmotion(
            byte[] imageBytes, String contentType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
                @Override public String getFilename() { return "image.jpg"; }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", imageResource);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://127.0.0.1:5001/analyze-image",
                    new HttpEntity<>(body, headers), Map.class);

            int level = ((Number) response.getBody()
                    .getOrDefault("emotionLevel", -1)).intValue();
            double confidence = ((Number) response.getBody()
                    .getOrDefault("confidence", 0.0)).doubleValue();

            log.info("图片情绪分析: L{}, confidence={}", level, confidence);
            return new EmotionAnalysisResult(level, confidence);

        } catch (Exception e) {
            log.warn("图片情绪分析失败: {}", e.getMessage());
            return new EmotionAnalysisResult(-1, 0.0);
        }
    }

    // ===== 动态权重融合 =====
    public int fuseEmotions(int textLevel,
                            int voiceLevel, double voiceConf,
                            int imageLevel, double imageConf) {
        double textWeight = 0.6;
        double voiceWeight = 0.0;
        double imageWeight = 0.0;

        if (voiceLevel != -1) {
            if (voiceConf >= 0.8)      { voiceWeight = 0.4; textWeight = 0.4; }
            else if (voiceConf >= 0.5) { voiceWeight = 0.3; textWeight = 0.5; }
            else                       { voiceWeight = 0.15; }
        }

        if (imageLevel != -1) {
            if (imageConf >= 0.5)      { imageWeight = 0.2; textWeight -= 0.2; }
            else if (imageConf >= 0.3) { imageWeight = 0.1; textWeight -= 0.1; }
        }

        double total = textWeight + voiceWeight + imageWeight;
        textWeight /= total;
        voiceWeight /= total;
        imageWeight /= total;

        double score = textLevel * textWeight;
        if (voiceLevel != -1) score += voiceLevel * voiceWeight;
        if (imageLevel != -1) score += imageLevel * imageWeight;

        int result = Math.max(1, Math.min(5, (int) Math.round(score)));

        log.info("动态权重融合: 文字L{}({}) + 语音L{}(conf={},w={}) + 图片L{}(conf={},w={}) → L{}",
                textLevel, String.format("%.2f", textWeight),
                voiceLevel, String.format("%.2f", voiceConf),
                String.format("%.2f", voiceWeight),
                imageLevel, String.format("%.2f", imageConf),
                String.format("%.2f", imageWeight), result);

        return result;
    }
}
// src/main/java/com/ling/linginnerflow/controller/WhisperController.java
package com.ling.linginnerflow.controller;

import com.ling.linginnerflow.multimodal.EmotionFusionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.ling.linginnerflow.multimodal.EmotionAnalysisResult;


import java.io.File;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/whisper")
@RequiredArgsConstructor
public class WhisperController {

    private final RestTemplate restTemplate;
    private final EmotionFusionService emotionFusionService;

    @Value("${spring.ai.openai.api-key}")
    private String openaiApiKey;

    @PostMapping("/transcribe")
    public Map<String, Object> transcribe(
            @RequestParam("audio") MultipartFile audio) {
        try {
            log.info("收到语音文件: size={}bytes", audio.getSize());

            File tempWebm = File.createTempFile("audio_", ".webm");
            audio.transferTo(tempWebm);

            File tempWav = File.createTempFile("audio_", ".wav");
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", tempWebm.getAbsolutePath(),
                    "-ar", "16000", "-ac", "1", "-f", "wav",
                    tempWav.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            pb.start().waitFor();

            byte[] wavBytes = java.nio.file.Files.readAllBytes(tempWav.toPath());

            // 并行执行
            CompletableFuture<String> textFuture = CompletableFuture
                    .supplyAsync(() -> callWhisper(wavBytes));

            CompletableFuture<EmotionAnalysisResult> voiceFuture = CompletableFuture
                    .supplyAsync(() -> emotionFusionService.analyzeVoiceEmotion(wavBytes));

            String text = textFuture.get(30, TimeUnit.SECONDS);
            com.ling.linginnerflow.multimodal.EmotionAnalysisResult voiceResult =
                    voiceFuture.get(30, TimeUnit.SECONDS);

            tempWebm.delete();
            tempWav.delete();

            log.info("语音转文字: {}", text);

            return Map.of(
                    "text", text != null ? text : "",
                    "voiceEmotionLevel", voiceResult.getLevel(),
                    "voiceConfidence", voiceResult.getConfidence()
            );

        } catch (Exception e) {
            log.error("处理失败: {}", e.getMessage());
            return Map.of("text", "", "voiceEmotionLevel", -1, "voiceConfidence", 0.0);
        }
    }
    private String callWhisper(byte[] wavBytes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + openaiApiKey);

            ByteArrayResource wavResource =
                    new ByteArrayResource(wavBytes) {
                        @Override
                        public String getFilename() { return "audio.wav"; }
                    };

            MultiValueMap<String, Object> body =
                    new LinkedMultiValueMap<>();
            body.add("file", wavResource);
            body.add("model", "whisper-1");
            body.add("language", "zh");

            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/audio/transcriptions",
                    request, Map.class
            );
            return (String) response.getBody().get("text");
        } catch (Exception e) {
            log.error("Whisper调用失败: {}", e.getMessage());
            return "";
        }
    }
}
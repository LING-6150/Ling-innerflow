// src/main/java/com/ling/linginnerflow/controller/WhisperController.java
package com.ling.linginnerflow.controller;

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

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/whisper")
@RequiredArgsConstructor
public class WhisperController {

    private final RestTemplate restTemplate;

    @Value("${spring.ai.openai.api-key}")
    private String openaiApiKey;

    @PostMapping("/transcribe")
    public Map<String, String> transcribe(@RequestParam("audio") MultipartFile audio) {
        try {
            log.info("收到语音文件: size={}bytes, type={}",
                    audio.getSize(), audio.getContentType());

            // 构建multipart请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + openaiApiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // 音频文件
            // 改成
            String contentType = audio.getContentType() != null ? audio.getContentType() : "";
            String ext = contentType.contains("mp4") ? "mp4" : "webm";
            ByteArrayResource audioResource = new ByteArrayResource(audio.getBytes()) {
                @Override
                public String getFilename() {
                    return "audio." + ext;
                }
            };

            body.add("file", audioResource);
            body.add("model", "whisper-1");
            body.add("language", "zh");

            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            // 调Whisper API
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/audio/transcriptions",
                    request,
                    Map.class
            );

            String text = (String) response.getBody().get("text");
            log.info("Whisper转录结果: {}", text);

            return Map.of("text", text != null ? text : "");

        } catch (Exception e) {
            log.error("Whisper转录失败: {}", e.getMessage());
            return Map.of("text", "", "error", e.getMessage());
        }
    }
}
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

import java.io.File;
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

            // 1. 把webm存到临时文件
            File tempWebm = File.createTempFile("audio_", ".webm");
            audio.transferTo(tempWebm);

            // 2. 用FFmpeg转成wav
            File tempWav = File.createTempFile("audio_", ".wav");
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", tempWebm.getAbsolutePath(),
                    "-ar", "16000",  // 16kHz采样率
                    "-ac", "1",      // 单声道
                    "-f", "wav",
                    tempWav.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor();

            // 3. 读取wav文件
            byte[] wavBytes = java.nio.file.Files.readAllBytes(tempWav.toPath());

            // 4. 上传Whisper
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + openaiApiKey);

            ByteArrayResource wavResource = new ByteArrayResource(wavBytes) {
                @Override
                public String getFilename() { return "audio.wav"; }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", wavResource);
            body.add("model", "whisper-1");
            body.add("language", "zh");

            HttpEntity<MultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/audio/transcriptions",
                    request, Map.class
            );

            // 5. 清理临时文件
            tempWebm.delete();
            tempWav.delete();

            String text = (String) response.getBody().get("text");
            log.info("Whisper转录结果: {}", text);
            return Map.of("text", text != null ? text : "");

        } catch (Exception e) {
            log.error("Whisper转录失败: {}", e.getMessage());
            return Map.of("text", "", "error", e.getMessage());
        }
    }
}
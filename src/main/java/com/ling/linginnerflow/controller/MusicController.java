package com.ling.linginnerflow.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/music")
public class MusicController {

    // 直接返回音乐列表，用可访问的URL
    @GetMapping("/list")
    public List<Map<String, String>> getMusicList() {
        return List.of(
                Map.of("title", "Forest Rain",
                        "url", "https://assets.mixkit.co/music/preview/mixkit-relaxing-in-nature-522.mp3"),
                Map.of("title", "Peaceful Mind",
                        "url", "https://assets.mixkit.co/music/preview/mixkit-serene-view-443.mp3"),
                Map.of("title", "Calm Waves",
                        "url", "https://assets.mixkit.co/music/preview/mixkit-life-is-a-dream-837.mp3"),
                Map.of("title", "Soft Piano",
                        "url", "https://assets.mixkit.co/music/preview/mixkit-piano-reflection-152.mp3")
        );
    }
}
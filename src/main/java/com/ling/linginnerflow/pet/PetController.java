// src/main/java/com/ling/linginnerflow/pet/PetController.java
package com.ling.linginnerflow.pet;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/pet")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final PetGreetingService petGreetingService;

    // 查询宠物状态
    @GetMapping
    public PetStatus getPet() {
        return petService.getOrCreate(getUserId());
    }

    // 记忆感问候（镜子模型）—— 进入宠物页时获取
    @GetMapping("/greeting")
    public Map<String, String> greeting() {
        return Map.of("greeting", petGreetingService.greet(getUserId()));
    }

    // Tap增加生命力
    @PostMapping("/tap")
    public PetStatus tap(@RequestBody Map<String, Integer> body) {
        int count = body.getOrDefault("count", 1);
        return petService.addVitality(getUserId(), count);
    }

    private String getUserId() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return (String) auth.getPrincipal();
    }
}
package com.ling.linginnerflow.controller;

import com.ling.linginnerflow.agent.EmotionGraph;
import com.ling.linginnerflow.emotion.EmotionLogService;
import com.ling.linginnerflow.memory.MemoryService;
import com.ling.linginnerflow.memory.Persona;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
public class EmotionController {

    private final EmotionGraph emotionGraph;
    private final MemoryService memoryService;
    private final EmotionLogService emotionLogService;

    /**
     * 情绪分析接口（带记忆版）
     * userId从JWT Token自动解析，前端不需要手动传
     */
    @PostMapping("/analyze")
    public Map<String, Object> analyze(
            @RequestBody AnalyzeRequest request) throws Exception {

        // 从JWT Token解析userId，不需要前端传
        String userId = getUserIdFromToken();
        String userInput = request.getUserInput();

        // 第一步：把用户消息存入短期记忆
        memoryService.addMessage(userId, "user", userInput);

        // 第二步：构建带历史上下文的输入
        Map<String, Object> input = new HashMap<>();
        input.put("userInput", userInput);
        input.put("userId", userId);

        // 第三步：调用EmotionGraph
        AgentState finalState = emotionGraph.buildGraph()
                .invoke(input).get();

        // 第四步：把AI回复存入短期记忆
        String aiResponse = (String) finalState.data()
                .getOrDefault("response", "");
        memoryService.addMessage(userId, "assistant", aiResponse);

        // 第五步：记录情绪日志
        emotionLogService.log(
                userId,
                (int) finalState.data().getOrDefault("emotionLevel", 1),
                userInput,
                aiResponse,
                "chat"
        );

        return new HashMap<>(finalState.data());
    }

    /**
     * 结束会话，触发长期记忆更新
     */
    @PostMapping("/end-session")
    public Map<String, String> endSession() {
        String userId = getUserIdFromToken();

        var history = memoryService.getShortMemory(userId);
        if (!history.isEmpty()) {
            memoryService.updateLongMemory(userId, history);
        }
        memoryService.clearShortMemory(userId);

        log.info("会话结束，记忆已更新: userId={}", userId);
        return Map.of(
                "status", "ok",
                "message", "会话已结束，记忆已保存"
        );
    }

    /**
     * 查询用户长期记忆
     */
    @GetMapping("/memory")
    public Object getMemory() {
        String userId = getUserIdFromToken();
        var memory = memoryService.getLongMemory(userId);
        if (memory == null) {
            return Map.of("message", "暂无长期记忆");
        }
        return memory;
    }

    /**
     * 从JWT Token解析userId
     * JwtAuthFilter已把userId存入SecurityContext
     */
    private String getUserIdFromToken() {
        Authentication auth = SecurityContextHolder.getContext()
                .getAuthentication();
        return (String) auth.getPrincipal();
    }

    @Data
    public static class AnalyzeRequest {
        private String userInput;
        // userId从Token解析，不需要前端传
    }

    @PostMapping("/persona")
    public Map<String, Object> setPersona(@RequestBody Map<String, String> body) {
        String userId = getUserIdFromToken();
        String personaStr = body.getOrDefault("persona", "WARM").toUpperCase();
        try {
            Persona persona = Persona.valueOf(personaStr);
            memoryService.setPersona(userId, persona);
            return Map.of("success", true, "persona", persona.name());
        } catch (Exception e) {
            return Map.of("success", false, "message", "无效的人格类型");
        }
    }

    @GetMapping("/persona")
    public Map<String, Object> getPersona() {
        String userId = getUserIdFromToken();
        Persona persona = memoryService.getPersona(userId);
        return Map.of("persona", persona.name());
    }
}
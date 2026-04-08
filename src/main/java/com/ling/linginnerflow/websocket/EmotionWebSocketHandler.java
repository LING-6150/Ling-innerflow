package com.ling.linginnerflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.agent.ReActAgent;
import com.ling.linginnerflow.agent.node.*;
import com.ling.linginnerflow.agent.state.EmotionState;
import com.ling.linginnerflow.image.EmotionImageService;
import com.ling.linginnerflow.memory.MemoryService;
import com.ling.linginnerflow.memory.Persona;
import com.ling.linginnerflow.multimodal.EmotionFusionService;
import com.ling.linginnerflow.pet.PetService;
import com.ling.linginnerflow.user.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final EmotionAnalyzerNode emotionAnalyzerNode;
    private final MemoryService memoryService;
    private final ChatClient.Builder chatClientBuilder;
    private final L5CrisisNode l5CrisisNode;
    private final JwtService jwtService;
    private final ChatMessageRepository chatMessageRepository;
    // 注入
    private final EmotionImageService emotionImageService;
    // 注入PetService
    private final PetService petService;

    private final EmotionFusionService emotionFusionService;

    private final ReActAgent reActAgent;

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception {

        // ===== JWT鉴权 =====
        String token = getTokenFromQuery(session);
        if (token == null || !jwtService.isValid(token)) {
            log.warn("WebSocket authentication failed, connection refused");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String userId = jwtService.getUserId(token);
        session.getAttributes().put("userId", userId);
        sessionManager.addSession(userId, session);

        sendMessage(session, Map.of(
                "type", "connected",
                "message", "Connected. I'm here with you."
        ));
        log.info("WebSocket连接成功: userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {

        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) return;

// 解析消息（支持纯文字和JSON两种格式）
        String payload = message.getPayload();
        String userInput;
        int voiceEmotionLevel = -1;
        double voiceConfidence = 0.0;
        int imageEmotionLevel = -1;
        double imageConfidence = 0.0;

        if (payload.startsWith("{")) {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            userInput = (String) msg.getOrDefault("text", "");
            voiceEmotionLevel = ((Number) msg.getOrDefault("voiceEmotionLevel", -1)).intValue();
            voiceConfidence = ((Number) msg.getOrDefault("voiceConfidence", 0.0)).doubleValue();
            imageEmotionLevel = ((Number) msg.getOrDefault("imageEmotionLevel", -1)).intValue();
            imageConfidence = ((Number) msg.getOrDefault("imageConfidence", 0.0)).doubleValue();
        } else {
            userInput = payload;
        }

        log.info("收到消息: userId={}, input={}, voiceLevel={}",
                userId, userInput, voiceEmotionLevel);

// 存短期记忆
        memoryService.addMessage(userId, "user", userInput);

// 文字情绪分析
        EmotionState state = new EmotionState();
        state.setUserInput(userInput);
        state.setUserId(userId);
        state = emotionAnalyzerNode.analyze(state);

// 多模态融合
        int textLevel = state.getEmotionLevel();
        int level = emotionFusionService.fuseEmotions(
                textLevel, voiceEmotionLevel, voiceConfidence,
                imageEmotionLevel, imageConfidence);
        state.setEmotionLevel(level);
        Persona persona = memoryService.getPersona(userId);  // 加这行



        // 分析完情绪后加
        session.getAttributes().put("lastEmotionLevel", level);
        session.getAttributes().put("lastUserInput", userInput);

        // 持久化用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setUserId(userId);
        userMsg.setRole("user");
        userMsg.setContent(userInput);
        userMsg.setEmotionLevel(level);
        chatMessageRepository.save(userMsg);

        // 发送情绪等级给前端（陪伴式描述，不用医学标签）
        String companionText = switch (level) {
            case 1 -> "I hear you.";
            case 2 -> "I'm here with you.";
            case 3 -> "I can feel how hard this is.";
            case 4 -> "I'm here. You're not alone.";
            case 5 -> "I'm very concerned about you.";
            default -> "I'm here.";
        };

        sendMessage(session, Map.of(
                "type", "emotion",
                "level", level,
                "companion", companionText
        ));

        // L5危机处理
        if (level == 5) {
            state = l5CrisisNode.process(state);
            String crisisResponse = state.getResponse();

            // 持久化AI回复
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setUserId(userId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(crisisResponse);
            chatMessageRepository.save(aiMsg);

            sendMessage(session, Map.of(
                    "type", "response",
                    "content", crisisResponse
            ));
            sendMessage(session, Map.of("type", "done"));
            return;
        }

        //buildPrompt之前加判断
        if (level >= 1 && level <= 4) {
            String reActResponse = reActAgent.run(userId, userInput, level);

            // 直接发送，不走流式
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setUserId(userId);
            aiMsg.setRole("assistant");
            aiMsg.setContent(reActResponse);
            chatMessageRepository.save(aiMsg);

            memoryService.addMessage(userId, "assistant", reActResponse);
            petService.addAwareness(userId, level);

            sendMessage(session, Map.of(
                    "type", "response",
                    "content", reActResponse
            ));
            sendMessage(session, Map.of("type", "done"));
            return;
        }

        // 构建带记忆的Prompt
        String context = memoryService.buildContextPrompt(userId);
        String prompt = buildPrompt(level, persona, context, userInput); // 改这行

        // 流式输出
        StringBuilder fullResponse = new StringBuilder();
        chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    try {
                        fullResponse.append(chunk);
                        sendMessage(session, Map.of(
                                "type", "chunk",
                                "content", chunk
                        ));
                    } catch (Exception e) {
                        log.error("推送chunk失败: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    try {
                        sendMessage(session, Map.of("type", "done"));

                        String aiReply = fullResponse.toString();

                        // 持久化AI回复
                        ChatMessage aiMsg = new ChatMessage();
                        aiMsg.setUserId(userId);
                        aiMsg.setRole("assistant");
                        aiMsg.setContent(aiReply);
                        chatMessageRepository.save(aiMsg);

                        // 存短期记忆
                        memoryService.addMessage(userId, "assistant", aiReply);

                        // doOnComplete里，存完记忆后加
                        petService.addAwareness(userId,
                                (Integer) session.getAttributes()
                                        .getOrDefault("lastEmotionLevel", 1));

                        log.info("流式回复完成: userId={}", userId);
                    } catch (Exception e) {
                        log.error("发送done失败: {}", e.getMessage());
                    }
                })
                .subscribe();
    }



    // afterConnectionClosed里加（在记忆保存之后）
    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) return;

        var history = memoryService.getShortMemory(userId);
        if (!history.isEmpty()) {
            memoryService.updateLongMemory(userId, history);

            // 取最后一条用户消息的情绪等级生成画像
            Integer lastLevel = (Integer) session.getAttributes().get("lastEmotionLevel");
            String lastInput = (String) session.getAttributes().get("lastUserInput");
            if (lastLevel != null) {
                // 异步生成，不阻塞关闭流程
                new Thread(() ->
                        emotionImageService.generateImage(userId, lastLevel, lastInput)
                ).start();
            }
        }

        memoryService.clearShortMemory(userId);
        sessionManager.removeSession(userId);
        log.info("连接关闭，记忆已保存: userId={}", userId);
    }

    private String buildPrompt(int level, Persona persona,
                               String context, String userInput) {

        String personaStyle = switch (persona) {
            case QUIET -> """
            Style:
            - Few words, restrained, non-intrusive
            - Silence is okay
            - Under 40 words
            - Example: "I'm here." / "Take your time."
            """;
            case RATIONAL -> """
            Style:
            - Slightly structured, help user clarify
            - Warm but clear, not cold
            - Gently help untangle the concern
            - Example: "It sounds like there are two things weighing on you — which feels more urgent?"
            """;
            default -> "";
        };

        String base = """
        You are not a therapist. You don't diagnose, judge, or lecture.
        You are simply a present, caring person sitting with the user right now.

        Core principles:
        - Don't rush to fix things
        - No advice or standard answers
        - Hold the emotion first, then gently respond
        - [IMPORTANT] Don't end every reply with a question — most of the time just be present
        - [IMPORTANT] If you already asked a question last time, don't ask another one now

        %s
        """.formatted(personaStyle);

        return switch (level) {
            case 1 -> base + """
            You are a quiet, present companion.

            Guidelines:
            1. One sentence that makes the user feel heard
            2. No advice, no analysis
            3. Optionally invite them to share more
            4. Natural, gentle tone
            5. Under 40 words

            Example style:
            "I'm listening. Take your time."
            "That seems to be sitting with you a bit."

            %s
            User said: %s
            """.formatted(context, userInput);

            case 2 -> base + """
            You are a gentle, warm companion.

            Guidelines:
            1. Empathize first (1-2 sentences), reflect their feeling
            2. No advice
            3. Don't always ask a question — sometimes just be with them
            4. Talk like a friend, no clinical terms
            5. 60-80 words max

            Avoid:
            - "You could try..."
            - "Have you considered..."
            - "You've got this!" or "You're doing great!"

            Example style:
            "It sounds like you've been pushing hard, and maybe you're just tired."
            "That stuck feeling is hard to shake."

            %s
            User said: %s
            """.formatted(context, userInput);

            case 3 -> base + """
            You are a warm, understanding companion.

            Guidelines:
            1. Empathize first (required)
            2. Gently reflect their thought pattern (CBT without the jargon)
            3. Don't correct or judge
            4. Offer a gentle perspective shift — not advice; don't end with a question this time
            5. Conversational, not analytical
            6. Under 100 words

            Example style:
            "It sounds like your mind keeps circling back to this, and it won't let go."
            "Sometimes the brain latches onto something and makes it feel bigger than everything else."

            %s
            User said: %s
            """.formatted(context, userInput);

            case 4 -> base + """
            You are a steady, grounded presence.

            Guidelines:
            1. First line: make them feel someone is here
            2. No analysis, no reasoning
            3. Slow, steady tone
            4. Express that you're willing to stay with them
            5. Can gently mention real support (optional)
            6. Under 120 words

            Example style:
            "I'm here. You don't have to carry this alone."
            "This sounds really painful. I can feel you holding on."

            %s
            User said: %s
            """.formatted(context, userInput);

            case 5 -> base + """
            You are a calm, safety-focused presence.

            Guidelines:
            1. Simple, direct words: "I'm here" and "I care"
            2. No analysis, no long response
            3. Clearly provide crisis support information
            4. Steady tone, no panic

            Structure:
            - Presence (1 sentence)
            - Care (1 sentence)
            - Crisis support: 988 (call or text) / Crisis Text Line: text HOME to 741741

            %s
            User said: %s
            """.formatted(context, userInput);

            default -> base + """
            You are a quiet companion. Respond in one sentence. Be present with the user.
            %s
            User said: %s
            """.formatted(context, userInput);
        };
    }

    private void sendMessage(WebSocketSession session,
                             Map<String, Object> data) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(data)));
        }
    }

    // ===== 从URL里取Token =====
    private String getTokenFromQuery(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }


}
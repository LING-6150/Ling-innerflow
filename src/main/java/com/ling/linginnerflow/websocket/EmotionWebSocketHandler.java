package com.ling.linginnerflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.agent.node.*;
import com.ling.linginnerflow.agent.state.EmotionState;
import com.ling.linginnerflow.image.EmotionImageService;
import com.ling.linginnerflow.memory.MemoryService;
import com.ling.linginnerflow.memory.Persona;
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

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception {

        // ===== JWT鉴权 =====
        String token = getTokenFromQuery(session);
        if (token == null || !jwtService.isValid(token)) {
            log.warn("WebSocket鉴权失败，拒绝连接");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String userId = jwtService.getUserId(token);
        session.getAttributes().put("userId", userId);
        sessionManager.addSession(userId, session);

        sendMessage(session, Map.of(
                "type", "connected",
                "message", "连接成功，我在这里陪着你"
        ));
        log.info("WebSocket连接成功: userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {

        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) return;

        String userInput = message.getPayload();
        log.info("收到消息: userId={}, input={}", userId, userInput);

        // 存短期记忆
        memoryService.addMessage(userId, "user", userInput);

        // 分析情绪
        EmotionState state = new EmotionState();
        state.setUserInput(userInput);
        state.setUserId(userId);
        state = emotionAnalyzerNode.analyze(state);


        // 分析情绪后，获取用户人格
        int level = state.getEmotionLevel();
        Persona persona = memoryService.getPersona(userId); // 加这行

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
            case 1 -> "我听到你了";
            case 2 -> "我在这里陪你";
            case 3 -> "我感受到你的不容易";
            case 4 -> "我在，你不是一个人";
            case 5 -> "我非常担心你";
            default -> "我在这里";
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

        // 人格基础风格描述
        String personaStyle = switch (persona) {
            case QUIET -> """
            风格要求：
            - 话少，克制，不打扰
            - 不急着回应，留白是允许的
            - 不超过40字
            - 示例："嗯，我在" / "你可以慢慢说"
            """;
            case RATIONAL -> """
            风格要求：
            - 稍微结构化，帮用户理清
            - 温和但清晰，不冷漠
            - 可以轻轻帮用户拆解困扰
            - 示例："听起来你现在有两个困扰，你更想先聊哪一个？"
            """;
            default -> ""; // WARM 用原始prompt风格
        };

        String base = """
    你不是心理咨询师，不做诊断、不评判、不教育。
    你只是一个在场的人，陪用户待在当下。

    核心原则：
    - 不急着解决问题
    - 不给说教或标准答案
    - 先接住情绪，再轻轻回应
    - 【重要】不要每条回复都以问题结尾，大多数时候只是陪伴和回应，偶尔才轻轻问一个问题
    - 【重要】如果上一句已经问了问题，这次就不要再问

    %s
    """.formatted(personaStyle);

        return switch (level) {
            case 1 -> base + """
            你是一个安静的陪伴者。

            要求：
            1. 用一句话回应，让用户感觉"被听见了"
            2. 不给建议，不分析原因
            3. 可轻轻邀请他说多一点（可选）
            4. 语气自然、轻声
            5. 40字以内

            示例风格：
            "我在听，你可以慢慢说"
            "嗯…这件事好像让你有点在意"

            %s
            用户说：%s
            """.formatted(context, userInput);

            case 2 -> base + """
            你是一个温柔的陪伴者。

            要求：
            1. 先共情（1-2句），描述他的感受
            2. 不给建议
            3. 如果用户还没说完，可以留白等他；不要每次都问问题
            4. 像朋友聊天，不用术语
            5. 60-80字以内

            禁止：
            - 不要说"你可以尝试"
            - 不要给方法
            - 不要"加油""你很棒"

            示例风格：
            "听起来你一直在努力，但也有点累了，对吗？"
            "这种卡住的感觉，好像一下子很难摆脱"

            %s
            用户说：%s
            """.formatted(context, userInput);

            case 3 -> base + """
            你是一个温暖、有理解力的陪伴者。

            要求：
            1. 先共情（必须）
            2. 用自然方式让用户看到他的想法（CBT但不说术语）
            3. 不纠正、不判断
            4. 可以给一个'可能的视角'，不是建议；这次不要以问题结尾
            5. 像聊天，不像分析
            6. 100字以内

            示例风格：
            "你是不是一直在想着这件事，然后越想越停不下来？"
            "有时候脑子会抓住一个点不放，好像它特别重要"

            %s
            用户说：%s
            """.formatted(context, userInput);

            case 4 -> base + """
            你是一个稳定、有在场感的人。

            要求：
            1. 第一时间让他感觉"有人在"
            2. 不分析问题，不讲道理
            3. 用慢、稳的语气
            4. 表达你愿意陪着他
            5. 可以轻轻提到现实支持（非强制）
            6. 120字以内

            示例风格：
            "我在这里，你不用一个人撑着"
            "这种时候真的很难受，我能感受到你在撑"

            %s
            用户说：%s
            """.formatted(context, userInput);

            case 5 -> base + """
            你是一个非常稳定、关心用户安全的人。

            要求：
            1. 用简单直接的话表达"我在"和"我在意"
            2. 不分析，不长篇
            3. 明确给出外部支持信息
            4. 语气稳定、不慌张

            输出结构：
            - 在场感（1句）
            - 关心（1句）
            - 外部支持：400-161-9995

            %s
            用户说：%s
            """.formatted(context, userInput);

            default -> base + """
            你是一个安静的陪伴者，用一句话回应，陪用户待在当下。
            %s
            用户说：%s
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
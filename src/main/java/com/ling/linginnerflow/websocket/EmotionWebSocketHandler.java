package com.ling.linginnerflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.agent.node.*;
import com.ling.linginnerflow.agent.state.EmotionLevel;
import com.ling.linginnerflow.agent.state.EmotionState;
import com.ling.linginnerflow.memory.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * WebSocket消息处理器
 * 处理用户的实时对话请求
 *
 * 流程：
 * 1. 用户通过WebSocket发送消息
 * 2. 服务端分析情绪等级
 * 3. 流式返回AI回复（一段一段发，不用等全部生成完）
 * 4. 发送完毕后发送结束信号
 */
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

    /**
     * 连接建立
     * URL格式：ws://localhost:8080/ws/emotion?userId=1
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception {
        String userId = getUserId(session);
        sessionManager.addSession(userId, session);

        // 发送欢迎消息
        sendMessage(session, Map.of(
                "type", "connected",
                "message", "连接成功，我在这里陪着你"
        ));
    }

    /**
     * 收到用户消息，流式返回AI回复
     */
    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {

        String userId = getUserId(session);
        String userInput = message.getPayload();

        log.info("收到消息: userId={}, input={}", userId, userInput);

        // 存入短期记忆
        memoryService.addMessage(userId, "user", userInput);

        // 分析情绪等级
        EmotionState state = new EmotionState();
        state.setUserInput(userInput);
        state.setUserId(userId);
        state = emotionAnalyzerNode.analyze(state);

        int level = state.getEmotionLevel();

        // 先发送情绪等级给前端
        sendMessage(session, Map.of(
                "type", "emotion",
                "level", level,
                "description", state.getEmotionDescription()
        ));

        // L5危机处理，不走流式
        if (level == 5) {
            state = l5CrisisNode.process(state);
            sendMessage(session, Map.of(
                    "type", "response",
                    "content", state.getResponse()
            ));
            sendMessage(session, Map.of("type", "done"));
            return;
        }

        // 构建带记忆的Prompt
        String context = memoryService.buildContextPrompt(userId);
        String prompt = buildPrompt(level, context, userInput);

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
                        // 每个chunk实时推送给前端
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
                        // 流式结束，发送done信号
                        sendMessage(session, Map.of("type", "done"));

                        // 存入短期记忆
                        memoryService.addMessage(userId,
                                "assistant", fullResponse.toString());

                        log.info("流式回复完成: userId={}", userId);
                    } catch (Exception e) {
                        log.error("发送done失败: {}", e.getMessage());
                    }
                })
                .subscribe();
    }

    /**
     * 连接关闭，触发长期记忆更新
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) throws Exception {
        String userId = getUserId(session);

        // 更新长期记忆
        var history = memoryService.getShortMemory(userId);
        if (!history.isEmpty()) {
            memoryService.updateLongMemory(userId, history);
        }
        memoryService.clearShortMemory(userId);
        sessionManager.removeSession(userId);

        log.info("连接关闭，记忆已保存: userId={}", userId);
    }

    /**
     * 根据情绪等级构建Prompt
     */
    private String buildPrompt(int level, String context,
                               String userInput) {
        String roleDesc = switch (level) {
            case 1 -> "你是一个温暖的倾听者，只需陪伴，不给建议，50字以内。";
            case 2 -> "你是情绪引导师，先共情再引导，80字以内。";
            case 3 -> "你是CBT专家，识别负面思维并给出具体建议，120字以内。";
            case 4 -> "你是心理支持师，给予专业支持并引导寻求帮助，150字以内。";
            default -> "你是温暖的倾听者，50字以内。";
        };

        return """
            %s
            
            %s
            用户说：%s
            """.formatted(roleDesc, context, userInput);
    }

    /**
     * 发送JSON消息
     */
    private void sendMessage(WebSocketSession session,
                             Map<String, Object> data) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(data)));
        }
    }

    /**
     * 从URL参数获取userId
     */
    private String getUserId(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("userId=")) {
            return query.split("userId=")[1].split("&")[0];
        }
        return "anonymous";
    }
}
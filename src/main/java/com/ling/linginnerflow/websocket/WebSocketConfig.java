package com.ling.linginnerflow.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EmotionWebSocketHandler emotionWebSocketHandler;
    private final TapWebSocketHandler tapWebSocketHandler;

    public WebSocketConfig(
            EmotionWebSocketHandler emotionWebSocketHandler,
            TapWebSocketHandler tapWebSocketHandler) {
        this.emotionWebSocketHandler = emotionWebSocketHandler;
        this.tapWebSocketHandler = tapWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry) {
        // 情绪对话
        registry.addHandler(emotionWebSocketHandler, "/ws/emotion")
                .setAllowedOrigins("*");

        // Tap计数
        registry.addHandler(tapWebSocketHandler, "/ws/tap")
                .setAllowedOrigins("*");
    }
}
package com.ling.linginnerflow.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket会话管理器
 * 管理所有在线用户的WebSocket连接
 * 用ConcurrentHashMap保证线程安全
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    // userId → WebSocketSession
    private final ConcurrentHashMap<String, WebSocketSession>
            sessions = new ConcurrentHashMap<>();

    public void addSession(String userId, WebSocketSession session) {
        sessions.put(userId, session);
        log.info("用户连接: userId={}, 当前在线: {}",
                userId, sessions.size());
    }

    public void removeSession(String userId) {
        sessions.remove(userId);
        log.info("用户断开: userId={}, 当前在线: {}",
                userId, sessions.size());
    }

    public WebSocketSession getSession(String userId) {
        return sessions.get(userId);
    }

    public boolean hasSession(String userId) {
        return sessions.containsKey(userId);
    }
}

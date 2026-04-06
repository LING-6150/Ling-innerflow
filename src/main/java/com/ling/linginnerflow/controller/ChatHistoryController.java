// src/main/java/com/ling/linginnerflow/controller/ChatHistoryController.java
package com.ling.linginnerflow.controller;

import com.ling.linginnerflow.websocket.ChatMessage;
import com.ling.linginnerflow.websocket.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatMessageRepository chatMessageRepository;

    @GetMapping("/history")
    public List<ChatMessage> getHistory() {
        String userId = getUserIdFromToken();
        return chatMessageRepository
                .findTop50ByUserIdOrderByCreatedAtAsc(userId);
    }

    private String getUserIdFromToken() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return (String) auth.getPrincipal();
    }
}
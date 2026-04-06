// src/main/java/com/ling/linginnerflow/websocket/ChatMessageRepository.java
package com.ling.linginnerflow.websocket;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 查最近50条，按时间正序
    List<ChatMessage> findTop50ByUserIdOrderByCreatedAtAsc(String userId);
}
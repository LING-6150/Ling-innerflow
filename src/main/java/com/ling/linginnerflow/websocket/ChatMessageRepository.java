// src/main/java/com/ling/linginnerflow/websocket/ChatMessageRepository.java
package com.ling.linginnerflow.websocket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop50ByUserIdOrderByCreatedAtAsc(String userId);

    // 查询指定时间范围内某用户的用户消息（含情绪等级）
    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId " +
           "AND m.role = 'user' AND m.createdAt >= :since " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessage> findUserMessagesSince(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since);

    // 查询所有有过对话记录的不重复userId
    @Query("SELECT DISTINCT m.userId FROM ChatMessage m")
    List<String> findDistinctUserIds();

    // 查询某用户最新的一条user消息（用于取最新情绪等级）
    java.util.Optional<ChatMessage> findFirstByUserIdAndRoleOrderByCreatedAtDesc(
            String userId, String role);

    // 查询某用户指定时间段内的L5危机记录
    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId " +
           "AND m.role = 'user' AND m.emotionLevel = 5 " +
           "AND m.createdAt >= :since ORDER BY m.createdAt DESC")
    List<ChatMessage> findCrisisAlerts(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since);
}
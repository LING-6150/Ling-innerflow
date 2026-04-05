package com.ling.linginnerflow.emotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmotionLogRepository
        extends JpaRepository<EmotionLog, Long> {

    // 查询某用户的历史记录，按时间倒序
    List<EmotionLog> findByUserIdOrderByCreatedAtDesc(String userId);

    // 查询某用户某时间段内的记录
    List<EmotionLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            String userId, LocalDateTime start, LocalDateTime end);

    // 按天统计情绪等级平均值（用于趋势图）
    @Query("SELECT DATE(e.createdAt) as date, " +
            "AVG(e.emotionLevel) as avgLevel, " +
            "COUNT(e.id) as count " +
            "FROM EmotionLog e " +
            "WHERE e.userId = :userId " +
            "AND e.createdAt >= :start " +
            "GROUP BY DATE(e.createdAt) " +
            "ORDER BY DATE(e.createdAt) ASC")
    List<Object[]> findDailyStats(
            @Param("userId") String userId,
            @Param("start") LocalDateTime start);

    // 统计各情绪等级分布
    @Query("SELECT e.emotionLevel, COUNT(e.id) " +
            "FROM EmotionLog e " +
            "WHERE e.userId = :userId " +
            "AND e.createdAt >= :start " +
            "GROUP BY e.emotionLevel " +
            "ORDER BY e.emotionLevel ASC")
    List<Object[]> findEmotionDistribution(
            @Param("userId") String userId,
            @Param("start") LocalDateTime start);
}
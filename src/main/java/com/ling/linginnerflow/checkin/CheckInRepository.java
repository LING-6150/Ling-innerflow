package com.ling.linginnerflow.checkin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 打卡记录数据访问层
 * JpaRepository已内置基础CRUD，不需要写SQL
 */
@Repository
public interface CheckInRepository extends JpaRepository<com.ling.linginnerflow.checkin.CheckIn, Long> {

    // 查询某用户的所有打卡记录，按时间倒序
    List<com.ling.linginnerflow.checkin.CheckIn> findByUserIdOrderByCreatedAtDesc(String userId);

    // 查询所有公开的打卡记录，按时间倒序（树洞广场用）
    List<com.ling.linginnerflow.checkin.CheckIn> findByVisibilityOrderByCreatedAtDesc(String visibility);
}
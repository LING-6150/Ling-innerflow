package com.ling.linginnerflow.checkin;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    /**
     * 提交打卡
     * userId从JWT Token自动解析
     */
    @PostMapping
    public CheckIn submitCheckIn(@RequestBody CheckInRequest request)
            throws Exception {
        String userId = getUserIdFromToken();
        return checkInService.submitCheckIn(
                userId,
                request.getContent(),
                request.getVisibility()
        );
    }

    /**
     * 查询个人历史打卡
     * userId从JWT Token自动解析
     */
    @GetMapping("/history")
    public List<CheckIn> getUserHistory() {
        String userId = getUserIdFromToken();
        return checkInService.getUserHistory(userId);
    }

    /**
     * 查询公开树洞广场
     */
    @GetMapping("/wall")
    public List<CheckIn> getPublicWall() {
        return checkInService.getPublicWall();
    }

    private String getUserIdFromToken() {
        Authentication auth = SecurityContextHolder.getContext()
                .getAuthentication();
        return (String) auth.getPrincipal();
    }

    @Data
    public static class CheckInRequest {
        // userId从Token解析，不需要前端传
        private String content;
        private String visibility;
    }
}
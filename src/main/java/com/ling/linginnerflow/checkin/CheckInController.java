package com.ling.linginnerflow.checkin;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping
    public CheckIn submitCheckIn(@RequestBody CheckInRequest request) {
        String userId = getUserIdFromToken();
        return checkInService.submitCheckIn(
                userId,
                request.getContent(),
                request.getVisibility(),
                request.getNeedAI()
        );
    }

    @GetMapping("/history")
    public List<CheckIn> getUserHistory() {
        String userId = getUserIdFromToken();
        return checkInService.getUserHistory(userId);
    }

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
        private String content;
        private String visibility;
        private Boolean needAI = true;
    }

    private final CheckInReactionRepository reactionRepository;

    /**
     * 抱抱/取消抱抱
     */
    @PostMapping("/{id}/react")
    public Map<String, Object> react(@PathVariable Long id) {
        String userId = getUserIdFromToken();

        if (reactionRepository.existsByCheckInIdAndUserId(id, userId)) {
            // 已抱抱→取消
            reactionRepository.deleteByCheckInIdAndUserId(id, userId);
            long count = reactionRepository.countByCheckInId(id);
            return Map.of("hugged", false, "count", count);
        } else {
            // 未抱抱→添加
            CheckInReaction reaction = new CheckInReaction();
            reaction.setCheckInId(id);
            reaction.setUserId(userId);
            reactionRepository.save(reaction);
            long count = reactionRepository.countByCheckInId(id);
            return Map.of("hugged", true, "count", count);
        }
    }

    /**
     * 查询某条打卡的抱抱数量和当前用户是否抱抱
     */
    @GetMapping("/{id}/react")
    public Map<String, Object> getReact(@PathVariable Long id) {
        String userId = getUserIdFromToken();
        long count = reactionRepository.countByCheckInId(id);
        boolean hugged = reactionRepository
                .existsByCheckInIdAndUserId(id, userId);
        return Map.of("hugged", hugged, "count", count);
    }
}
package com.ling.linginnerflow.memory;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    /** 获取当前用户的 Wiki 摘要 */
    @GetMapping("/wiki")
    public ResponseEntity<MemoryService.WikiSummaryDto> getWiki() {
        return ResponseEntity.ok(memoryService.getWikiSummary(userId()));
    }

    /** 用户主动纠错 */
    @PostMapping("/wiki/correct")
    public ResponseEntity<Void> correct(@RequestBody CorrectionRequest req) {
        memoryService.applyUserCorrection(
                userId(),
                req.getField(),
                req.getObservationText(),
                req.getCorrectionType(),
                req.getCorrectionText()
        );
        return ResponseEntity.ok().build();
    }

    /** 用户清除所有记忆（CCPA 隐私权） */
    @DeleteMapping("/wiki")
    public ResponseEntity<Void> clearWiki() {
        memoryService.clearUserWiki(userId());
        return ResponseEntity.ok().build();
    }

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (String) auth.getPrincipal();
    }

    @Data
    public static class CorrectionRequest {
        private String field;           // triggers / emotionPattern / coreStruggles / effectiveCoping / languageStyle
        private String observationText; // 针对 triggers 的原始文本
        private String correctionType;  // correct / delete / add
        private String correctionText;  // 用户给出的正确内容
    }
}

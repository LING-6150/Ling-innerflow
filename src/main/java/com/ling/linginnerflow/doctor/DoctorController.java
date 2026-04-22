package com.ling.linginnerflow.doctor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Doctor Dashboard API
 * All endpoints return standard JSON with English field names.
 */
@Slf4j
@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    /**
     * GET /api/doctor/patients
     * Returns all users with their latest emotion level and last active time.
     *
     * Response: [{ userId, latestEmotionLevel, lastActiveAt }, ...]
     */
    @GetMapping("/patients")
    public ResponseEntity<List<Map<String, Object>>> getPatients() {
        log.info("[Doctor] GET /api/doctor/patients");
        return ResponseEntity.ok(doctorService.getPatientList());
    }

    /**
     * GET /api/doctor/patients/{userId}/emotion-trend
     * Returns daily average emotion level for the past 7 days.
     *
     * Response: [{ date, avgEmotionLevel, sessionCount }, ...]
     */
    @GetMapping("/patients/{userId}/emotion-trend")
    public ResponseEntity<List<Map<String, Object>>> getEmotionTrend(
            @PathVariable String userId) {
        log.info("[Doctor] GET /api/doctor/patients/{}/emotion-trend", userId);
        return ResponseEntity.ok(doctorService.getEmotionTrend(userId));
    }

    /**
     * GET /api/doctor/patients/{userId}/summary
     * Returns L4 Reflection, PHQ-9 screening estimate, emotion pattern,
     * core struggles, effective coping strategies, and overall trend report.
     *
     * Response: { userId, emotionPattern, coreStruggles, effectiveCoping,
     *             l4Reflection, phq9Screening, emotionTrendReport, lastActiveAt }
     */
    @GetMapping("/patients/{userId}/summary")
    public ResponseEntity<Map<String, Object>> getPatientSummary(
            @PathVariable String userId) {
        log.info("[Doctor] GET /api/doctor/patients/{}/summary", userId);
        return ResponseEntity.ok(doctorService.getPatientSummary(userId));
    }

    /**
     * GET /api/doctor/patients/{userId}/crisis-alerts
     * Returns all L5 crisis-level messages from the past 30 days.
     *
     * Response: [{ timestamp, content, emotionLevel }, ...]
     */
    @GetMapping("/patients/{userId}/crisis-alerts")
    public ResponseEntity<List<Map<String, Object>>> getCrisisAlerts(
            @PathVariable String userId) {
        log.info("[Doctor] GET /api/doctor/patients/{}/crisis-alerts", userId);
        return ResponseEntity.ok(doctorService.getCrisisAlerts(userId));
    }
}

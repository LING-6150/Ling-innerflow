package com.ling.linginnerflow.doctor;

import com.ling.linginnerflow.memory.MemoryService;
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
    private final MemoryService memoryService;

    @GetMapping("/patients")
    public ResponseEntity<List<Map<String, Object>>> getPatients() {
        log.info("[Doctor] GET /api/doctor/patients");
        return ResponseEntity.ok(doctorService.getPatientList());
    }

    @GetMapping("/patients/{userId}/emotion-trend")
    public ResponseEntity<List<Map<String, Object>>> getEmotionTrend(
            @PathVariable String userId,
            @RequestParam(defaultValue = "7") int days) {
        log.info("[Doctor] GET /api/doctor/patients/{}/emotion-trend?days={}", userId, days);
        return ResponseEntity.ok(doctorService.getEmotionTrend(userId, days));
    }

    @GetMapping("/patients/{userId}/summary")
    public ResponseEntity<Map<String, Object>> getPatientSummary(
            @PathVariable String userId) {
        log.info("[Doctor] GET /api/doctor/patients/{}/summary", userId);
        return ResponseEntity.ok(doctorService.getPatientSummary(userId));
    }

    @GetMapping("/patients/{userId}/crisis-alerts")
    public ResponseEntity<List<Map<String, Object>>> getCrisisAlerts(
            @PathVariable String userId) {
        log.info("[Doctor] GET /api/doctor/patients/{}/crisis-alerts", userId);
        return ResponseEntity.ok(doctorService.getCrisisAlerts(userId));
    }

    @GetMapping("/patients/{userId}/crisis-heatmap")
    public ResponseEntity<List<Map<String, Object>>> getCrisisHeatmap(
            @PathVariable String userId) {
        log.info("[Doctor] GET /api/doctor/patients/{}/crisis-heatmap", userId);
        return ResponseEntity.ok(doctorService.getCrisisHeatmap(userId));
    }

    /** Triggers L4 Reflection regeneration from stored memory data. */
    @PostMapping("/patients/{userId}/regenerate-reflection")
    public ResponseEntity<Map<String, Object>> regenerateReflection(
            @PathVariable String userId) {
        log.info("[Doctor] POST /api/doctor/patients/{}/regenerate-reflection", userId);
        memoryService.generateReflection(userId);
        return ResponseEntity.ok(doctorService.getPatientSummary(userId));
    }
}

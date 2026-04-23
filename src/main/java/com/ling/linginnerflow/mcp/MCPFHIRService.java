package com.ling.linginnerflow.mcp;

import com.ling.linginnerflow.agent.tool.EmotionTrendAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MCPFHIRService {

    private final RestTemplate restTemplate;
    private final EmotionTrendAnalyzer trendAnalyzer;

    private static final String FHIR_BASE =
            "https://hapi.fhir.org/baseR4";

    // 读取FHIR Patient
    public Map<String, Object> getPatient(String fhirPatientId) {
        try {
            return restTemplate.getForObject(
                    FHIR_BASE + "/Patient/" + fhirPatientId,
                    Map.class);
        } catch (Exception e) {
            log.warn("FHIR patient fetch failed: {}", e.getMessage());
            return Map.of("id", fhirPatientId, "name", "Unknown");
        }
    }

    // 生成FHIR Observation格式的情绪评估
    public String getPatientSummary(
            String fhirPatientId, String innerflowUserId) {
        try {
            Map<String, Object> patient = getPatient(fhirPatientId);
            String patientName = extractPatientName(patient);
            String gender = (String) patient.getOrDefault("gender", "unknown");
            String birthDate = (String) patient.getOrDefault("birthDate", "");
            int age = calculateAge(birthDate);

            // Build a readable subject display — fall back gracefully when FHIR demographics are missing
            String subjectDisplay;
            if ("Unknown Patient".equals(patientName) || age == 0) {
                subjectDisplay = "InnerFlow Patient #" + innerflowUserId +
                        (fhirPatientId != null ? " (FHIR/" + fhirPatientId + ")" : "");
            } else {
                subjectDisplay = patientName + ", " + gender + ", age " + age;
            }

            String trendData = trendAnalyzer.execute(innerflowUserId);

            // Brief note for human-readable annotation (not a duplicate of component)
            String briefNote = "AI-generated mental health observation for InnerFlow user #" +
                    innerflowUserId + ". Powered by Multimodal Emotion AI + CBT RAG. " +
                    "Not a clinical diagnosis.";

            java.util.LinkedHashMap<String, Object> observation = new java.util.LinkedHashMap<>();
            observation.put("resourceType", "Observation");
            observation.put("id", "innerflow-" + innerflowUserId + "-" +
                    java.time.Instant.now().getEpochSecond());
            observation.put("status", "final");
            observation.put("category", Map.of(
                    "coding", new Object[]{Map.of(
                            "system", "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code", "survey",
                            "display", "Survey"
                    )}
            ));
            observation.put("code", Map.of(
                    "coding", new Object[]{Map.of(
                            "system", "http://loinc.org",
                            "code", "89204-2",
                            "display", "Mental health assessment"
                    )},
                    "text", "InnerFlow Mental Health Emotion Assessment"
            ));
            observation.put("subject", Map.of(
                    "reference", "Patient/" + fhirPatientId,
                    "display", subjectDisplay
            ));
            observation.put("effectiveDateTime", java.time.Instant.now().toString());
            observation.put("note", new Object[]{Map.of("text", briefNote)});
            observation.put("component", new Object[]{Map.of(
                    "code", Map.of("text", "Emotion Trend Analysis"),
                    "valueString", trendData
            )});

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return "FHIR Observation:\n" +
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(observation);

        } catch (Exception e) {
            log.error("FHIR summary failed: {}", e.getMessage());
            return "FHIR summary generation failed: " + e.getMessage();
        }
    }

    private int calculateAge(String birthDate) {
        try {
            java.time.LocalDate birth =
                    java.time.LocalDate.parse(birthDate);
            return java.time.Period.between(
                    birth, java.time.LocalDate.now()).getYears();
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractPatientName(Map<String, Object> patient) {
        try {
            var names = (java.util.List<?>) patient.get("name");
            if (names != null && !names.isEmpty()) {
                var name = (Map<?, ?>) names.get(0);
                var given = (java.util.List<?>) name.get("given");
                String family = (String) name.get("family");
                String first = given != null && !given.isEmpty()
                        ? (String) given.get(0) : "";
                // 替换原来的 return first + " " + family;
                String fullName = first + " " + family;
                return new String(fullName.getBytes(
                        java.nio.charset.StandardCharsets.ISO_8859_1),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Could not extract patient name");
        }
        return "Unknown Patient";
    }
}
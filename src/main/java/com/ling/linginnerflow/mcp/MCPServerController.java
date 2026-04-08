package com.ling.linginnerflow.mcp;

import com.ling.linginnerflow.agent.tool.*;
import com.ling.linginnerflow.multimodal.EmotionFusionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class MCPServerController {

    private final HistoryContextRetriever historyRetriever;
    private final CBTSkillLibrary cbtLibrary;
    private final WellnessResourceSearch wellnessSearch;
    private final EmotionTrendAnalyzer trendAnalyzer;
    private final MCPFHIRService fhirService;

    // ===== MCP Server Info =====
    @GetMapping("/info")
    public Map<String, Object> serverInfo() {
        return Map.of(
                "name", "InnerFlow Mental Health MCP Server",
                "version", "1.0.0",
                "description", "AI-powered mental health assessment tools " +
                        "with multimodal emotion analysis and CBT interventions",
                "capabilities", Map.of(
                        "tools", true,
                        "fhir", true
                )
        );
    }

    // ===== List Tools =====
    @PostMapping("/tools/list")
    public ResponseEntity<MCPResponse> listTools(
            @RequestBody Map<String, Object> request) {

        String id = (String) request.getOrDefault("id", "1");

        List<MCPToolDefinition> tools = List.of(
                MCPToolDefinition.builder()
                        .name("emotion_trend_analyzer")
                        .description("Analyzes a patient's emotional trend over " +
                                "the past 7 days. Returns average emotion level, " +
                                "trend direction, and persistent distress indicators. " +
                                "Output includes FHIR Observation format.")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "patientId", Map.of(
                                                "type", "string",
                                                "description", "The patient's user ID"
                                        )
                                ),
                                "required", List.of("patientId")
                        ))
                        .build(),

                MCPToolDefinition.builder()
                        .name("cbt_intervention")
                        .description("Retrieves relevant CBT (Cognitive Behavioral " +
                                "Therapy) interventions based on a patient's expressed " +
                                "concern or thought pattern. Uses hybrid RAG search " +
                                "across 200 evidence-based CBT entries.")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "concern", Map.of(
                                                "type", "string",
                                                "description", "The patient's concern or " +
                                                        "thought pattern to search for"
                                        )
                                ),
                                "required", List.of("concern")
                        ))
                        .build(),

                MCPToolDefinition.builder()
                        .name("crisis_resource_search")
                        .description("Returns appropriate crisis support resources " +
                                "and hotlines based on the patient's current need. " +
                                "Covers breathing techniques, grounding exercises, " +
                                "and 24/7 crisis lines.")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "need", Map.of(
                                                "type", "string",
                                                "description", "The patient's specific need, " +
                                                        "e.g. 'breathing exercise', 'crisis line'"
                                        )
                                ),
                                "required", List.of("need")
                        ))
                        .build(),

                MCPToolDefinition.builder()
                        .name("conversation_history")
                        .description("Retrieves the recent conversation history " +
                                "for a patient, including emotion levels per session. " +
                                "Useful for understanding emotional context before " +
                                "a clinical encounter.")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "patientId", Map.of(
                                                "type", "string",
                                                "description", "The patient's user ID"
                                        )
                                ),
                                "required", List.of("patientId")
                        ))
                        .build(),

                MCPToolDefinition.builder()
                        .name("fhir_patient_summary")
                        .description("Fetches patient demographics from FHIR server " +
                                "and combines with InnerFlow emotion assessment to " +
                                "generate a clinical mental health summary in " +
                                "FHIR Observation format.")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "fhirPatientId", Map.of(
                                                "type", "string",
                                                "description", "The FHIR Patient resource ID"
                                        ),
                                        "innerflowUserId", Map.of(
                                                "type", "string",
                                                "description", "The InnerFlow user ID"
                                        )
                                ),
                                "required", List.of("fhirPatientId", "innerflowUserId")
                        ))
                        .build()
        );

        return ResponseEntity.ok(MCPResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .result(MCPResponse.ToolsResult.builder()
                        .tools(tools)
                        .build())
                .build());
    }

    // ===== Execute Tool =====
    @PostMapping("/tools/call")
    public ResponseEntity<MCPResponse> callTool(
            @RequestBody Map<String, Object> request) {

        String id = (String) request.getOrDefault("id", "1");
        Map<String, Object> params = (Map<String, Object>)
                request.get("params");
        String toolName = (String) params.get("name");
        Map<String, Object> args = (Map<String, Object>)
                params.get("arguments");

        log.info("[MCP] Tool called: {}, args: {}", toolName, args);

        try {
            String result = switch (toolName) {
                case "emotion_trend_analyzer" -> {
                    String patientId = (String) args.get("patientId");
                    yield trendAnalyzer.execute(patientId);
                }
                case "cbt_intervention" -> {
                    String concern = (String) args.get("concern");
                    yield cbtLibrary.execute(concern);
                }
                case "crisis_resource_search" -> {
                    String need = (String) args.get("need");
                    yield wellnessSearch.execute(need);
                }
                case "conversation_history" -> {
                    String patientId = (String) args.get("patientId");
                    yield historyRetriever.execute(patientId);
                }
                case "fhir_patient_summary" -> {
                    String fhirId = (String) args.get("fhirPatientId");
                    String userId = (String) args.get("innerflowUserId");
                    yield fhirService.getPatientSummary(fhirId, userId);
                }
                default -> "Unknown tool: " + toolName;
            };

            return ResponseEntity.ok(MCPResponse.builder()
                    .jsonrpc("2.0")
                    .id(id)
                    .result(MCPResponse.CallResult.builder()
                            .content(List.of(
                                    MCPResponse.ContentBlock.builder()
                                            .type("text")
                                            .text(result)
                                            .build()
                            ))
                            .isError(false)
                            .build())
                    .build());

        } catch (Exception e) {
            log.error("[MCP] Tool execution failed: {}", e.getMessage());
            return ResponseEntity.ok(MCPResponse.builder()
                    .jsonrpc("2.0")
                    .id(id)
                    .result(MCPResponse.CallResult.builder()
                            .content(List.of(
                                    MCPResponse.ContentBlock.builder()
                                            .type("text")
                                            .text("Tool execution failed: " + e.getMessage())
                                            .build()
                            ))
                            .isError(true)
                            .build())
                    .build());
        }
    }
}

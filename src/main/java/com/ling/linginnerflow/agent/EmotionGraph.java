package com.ling.linginnerflow.agent;

import com.ling.linginnerflow.agent.node.*;
import com.ling.linginnerflow.agent.state.EmotionState;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionGraph {

    private final EmotionAnalyzerNode analyzerNode;
    private final PlannerNode plannerNode;
    private final L1CompanionNode l1Node;
    private final L2GuidanceNode l2Node;
    private final L3CBTNode l3Node;
    private final L4ProfessionalNode l4Node;
    private final L5CrisisNode l5Node;
    private final ObservationRegistry observationRegistry;

    public Optional<AgentState> invoke(Map<String, Object> input) throws GraphStateException {
        Observation observation = Observation.createNotStarted("emotion.graph.invoke", observationRegistry)
                .lowCardinalityKeyValue("runtime", "langgraph")
                .start();

        try (Observation.Scope ignored = observation.openScope()) {
            Optional<AgentState> result = buildGraph().invoke(input);
            result.ifPresent(state -> tagLevels(observation, state.data()));
            return result;
        } catch (GraphStateException | RuntimeException e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    public CompiledGraph<AgentState> buildGraph() throws GraphStateException {

        var graph = new StateGraph<>(AgentState::new);

        graph.addNode("analyzer", node_async(state -> {
            return observeNode("analyzer", () -> {
                String input = state.value("userInput", "");
                String userId = state.value("userId", "anonymous");
                EmotionState es = new EmotionState();
                es.setUserInput(input);
                es.setUserId(userId);
                es = analyzerNode.analyze(es);
                return Map.of(
                        "emotionLevel", es.getEmotionLevel(),
                        "emotionDescription", es.getEmotionDescription(),
                        "crisisMode", es.isCrisisMode(),
                        "userId", userId
                );
            });
        }));

        // Planner 节点：读取 analyzer 输出 + 历史，决定 targetLevel
        graph.addNode("planner", node_async(state -> {
            return observeNode("planner", () -> {
                EmotionState es = extractState(state);
                es = plannerNode.plan(es);
                return Map.of(
                        "targetLevel", es.getTargetLevel(),
                        "strategy",    es.getStrategy(),
                        "toneHint",    es.getToneHint()
                );
            });
        }));

        // L1节点
        graph.addNode("l1", node_async(state -> {
            return observeNode("l1", () -> {
                EmotionState es = extractState(state);
                es = l1Node.process(es);
                return Map.of("response", es.getResponse());
            });
        }));

        // L2节点
        graph.addNode("l2", node_async(state -> {
            return observeNode("l2", () -> {
                EmotionState es = extractState(state);
                es = l2Node.process(es);
                return Map.of("response", es.getResponse());
            });
        }));

        // L3节点
        graph.addNode("l3", node_async(state -> {
            return observeNode("l3", () -> {
                EmotionState es = extractState(state);
                es = l3Node.process(es);
                return Map.of("response", es.getResponse());
            });
        }));

        // L4节点
        graph.addNode("l4", node_async(state -> {
            return observeNode("l4", () -> {
                EmotionState es = extractState(state);
                es = l4Node.process(es);
                return Map.of("response", es.getResponse());
            });
        }));

        // L5节点
        graph.addNode("l5", node_async(state -> {
            return observeNode("l5", () -> {
                EmotionState es = extractState(state);
                es = l5Node.process(es);
                return Map.of("response", es.getResponse());
            });
        }));

        // START → analyzer → planner → conditional route
        graph.addEdge(START, "analyzer");
        graph.addEdge("analyzer", "planner");

        // 路由依据 Planner 决定的 targetLevel，而非原始 emotionLevel
        AsyncEdgeAction<AgentState> router = state -> {
            int target = state.value("targetLevel", 1);
            // L5 安全保底：若 emotionLevel=5 但 targetLevel 未正确传入
            int raw = state.value("emotionLevel", 1);
            if (raw == 5) target = 5;
            String node = switch (target) {
                case 2 -> "l2";
                case 3 -> "l3";
                case 4 -> "l4";
                case 5 -> "l5";
                default -> "l1";
            };
            log.info("[Graph] routing → {} (targetLevel={}, rawLevel={})", node, target, raw);
            return java.util.concurrent.CompletableFuture.completedFuture(node);
        };

        graph.addConditionalEdges("planner", router,
                Map.of(
                        "l1", "l1",
                        "l2", "l2",
                        "l3", "l3",
                        "l4", "l4",
                        "l5", "l5"
                )
        );

        // 所有节点 → END
        graph.addEdge("l1", END);
        graph.addEdge("l2", END);
        graph.addEdge("l3", END);
        graph.addEdge("l4", END);
        graph.addEdge("l5", END);

        return graph.compile();
    }

    private EmotionState extractState(AgentState state) {
        EmotionState es = new EmotionState();
        es.setUserInput(state.value("userInput", ""));
        es.setUserId(state.value("userId", "anonymous"));
        es.setEmotionLevel(state.value("emotionLevel", 1));
        es.setEmotionDescription(state.value("emotionDescription", ""));
        es.setCrisisMode(state.value("crisisMode", false));
        // Planner context (default 0 / empty for CheckIn flow which has no session history)
        es.setPreviousLevel(state.value("previousLevel", 0));
        es.setTargetLevel(state.value("targetLevel", es.getEmotionLevel()));
        es.setStrategy(state.value("strategy", "pure"));
        es.setToneHint(state.value("toneHint", ""));
        return es;
    }

    private Map<String, Object> observeNode(String nodeName, Supplier<Map<String, Object>> supplier) {
        Observation observation = Observation.createNotStarted("node." + nodeName, observationRegistry)
                .lowCardinalityKeyValue("node.name", nodeName)
                .start();

        try (Observation.Scope ignored = observation.openScope()) {
            Map<String, Object> result = supplier.get();
            tagLevels(observation, result);
            return result;
        } catch (RuntimeException e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    private void tagLevels(Observation observation, Map<String, Object> values) {
        Object emotionLevel = values.get("emotionLevel");
        if (emotionLevel != null) {
            observation.lowCardinalityKeyValue("emotion.level", String.valueOf(emotionLevel));
        }

        Object routeLevel = values.get("targetLevel");
        if (routeLevel != null) {
            observation.lowCardinalityKeyValue("route.level", String.valueOf(routeLevel));
        }
    }
}

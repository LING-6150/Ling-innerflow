package com.ling.linginnerflow.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WellnessResourceSearch implements AgentTool {

    private static final List<Map<String, String>> RESOURCES = List.of(
            Map.of(
                    "type", "breathing",
                    "name", "4-7-8 Breathing",
                    "description", "Inhale for 4 seconds, hold for 7, exhale for 8. Repeat 4 times. Great for anxiety and sleep difficulty.",
                    "keywords", "anxiety stress sleep breathing relax calm"
            ),
            Map.of(
                    "type", "breathing",
                    "name", "Box Breathing",
                    "description", "Inhale 4 sec, hold 4 sec, exhale 4 sec, hold 4 sec. Quickly settles an activated nervous system.",
                    "keywords", "tense panic nervous calm quick focus"
            ),
            Map.of(
                    "type", "grounding",
                    "name", "5-4-3-2-1 Grounding",
                    "description", "Name 5 things you see, 4 you can touch, 3 you hear, 2 you smell, 1 you taste. Brings you back to the present moment.",
                    "keywords", "anxiety panic dissociate present grounding overwhelm"
            ),
            Map.of(
                    "type", "meditation",
                    "name", "Body Scan Meditation",
                    "description", "Slowly move attention from head to toe, releasing tension in each area. 10-15 minutes recommended.",
                    "keywords", "meditation relax tension body stress unwind"
            ),
            Map.of(
                    "type", "crisis",
                    "name", "988 Suicide & Crisis Lifeline",
                    "description", "Call or text 988. Free, confidential, 24/7. For anyone in emotional distress or suicidal crisis.",
                    "keywords", "crisis suicidal hopeless desperate help emergency"
            ),
            Map.of(
                    "type", "crisis",
                    "name", "Crisis Text Line",
                    "description", "Text HOME to 741741. Free 24/7 crisis support via text message.",
                    "keywords", "crisis text support urgent distress"
            ),
            Map.of(
                    "type", "crisis",
                    "name", "NAMI Helpline",
                    "description", "Call 1-800-950-6264. National Alliance on Mental Illness helpline. Mon-Fri 10am-10pm ET.",
                    "keywords", "mental health support helpline nami resources"
            ),
            Map.of(
                    "type", "sleep",
                    "name", "Pre-Sleep Wind Down",
                    "description", "1 hour before bed: dim lights, put away screens, do progressive muscle relaxation, visualize a calm place.",
                    "keywords", "sleep insomnia restless tired exhausted bedtime"
            ),
            Map.of(
                    "type", "journal",
                    "name", "Emotion Journaling",
                    "description", "Write: what happened today, what did I feel, what does this feeling tell me? Helps process and release emotions.",
                    "keywords", "journal write reflect emotion process feelings"
            ),
            Map.of(
                    "type", "social",
                    "name", "Reach Out to Someone You Trust",
                    "description", "Text or call a trusted friend or family member. You don't need to explain everything — just let them know you're having a hard time.",
                    "keywords", "lonely isolated alone friend family support connection"
            ),
            Map.of(
                    "type", "movement",
                    "name", "10-Minute Walk",
                    "description", "Step outside for a slow 10-minute walk. Focus on the ground under your feet, the air, and the sounds around you. Simple and effective.",
                    "keywords", "exercise walk outside move physical mood energy"
            )
    );

    @Override
    public String getName() {
        return "WellnessResourceSearch";
    }

    @Override
    public String getDescription() {
        return "Call this when the user needs specific relaxation techniques, " +
                "breathing exercises, meditation guidance, or crisis support lines. " +
                "Input: describe what the user needs, e.g. 'breathing exercise', 'can't sleep', 'crisis line'.";
    }

    @Override
    public String execute(String query) {
        try {
            log.info("[Tool] WellnessResourceSearch: query={}", query);

            List<Map<String, String>> matched = RESOURCES.stream()
                    .filter(r -> {
                        String keywords = r.get("keywords").toLowerCase();
                        String q = query.toLowerCase();
                        return java.util.Arrays.stream(q.split("\\s+"))
                                .anyMatch(keywords::contains)
                                || java.util.Arrays.stream(keywords.split("\\s+"))
                                .anyMatch(q::contains);
                    })
                    .limit(2)
                    .toList();

            if (matched.isEmpty()) {
                matched = List.of(RESOURCES.get(0), RESOURCES.get(2));
            }

            StringBuilder sb = new StringBuilder("Recommended resources:\n\n");
            matched.forEach(r -> {
                sb.append("【").append(r.get("name")).append("】\n");
                sb.append(r.get("description")).append("\n\n");
            });

            log.info("[Tool] WellnessResourceSearch: found {} resources", matched.size());
            return sb.toString();

        } catch (Exception e) {
            log.error("[Tool] WellnessResourceSearch failed: {}", e.getMessage());
            return "Resource search failed.";
        }
    }
}
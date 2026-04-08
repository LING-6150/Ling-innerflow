package com.ling.linginnerflow.agent.tool;

import com.ling.linginnerflow.rag.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CBTSkillLibrary implements AgentTool {

    private final HybridSearchService hybridSearchService;

    @Override
    public String getName() {
        return "CBTSkillLibrary";
    }

    @Override
    public String getDescription() {
        return "Call this when the user shows negative thought patterns " +
                "such as self-criticism, catastrophizing, or black-and-white thinking. " +
                "Retrieves targeted CBT intervention methods from the knowledge base. " +
                "Input: the user's specific concern or struggle.";
    }

    @Override
    public String execute(String userInput) {
        try {
            String cbtContent = hybridSearchService.hybridSearch(userInput);

            if (cbtContent == null || cbtContent.isEmpty()) {
                return "No relevant CBT content found.";
            }

            log.info("[Tool] CBTSkillLibrary: input={}, found {} chars",
                    userInput, cbtContent.length());
            return "Relevant CBT intervention:\n" + cbtContent;

        } catch (Exception e) {
            log.error("[Tool] CBTSkillLibrary failed: {}", e.getMessage());
            return "CBT knowledge base lookup failed.";
        }
    }
}
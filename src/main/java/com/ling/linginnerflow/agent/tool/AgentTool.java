package com.ling.linginnerflow.agent.tool;

public interface AgentTool {
    String getName();
    String getDescription();
    String execute(String input);
}
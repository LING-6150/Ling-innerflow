package com.ling.linginnerflow.mcp;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class MCPResponse {
    private String jsonrpc;
    private String id;
    private Object result;
    private MCPError error;

    @Data
    @Builder
    public static class MCPError {
        private int code;
        private String message;
    }

    // 工具列表响应
    @Data
    @Builder
    public static class ToolsResult {
        private List<MCPToolDefinition> tools;
    }

    // 工具执行响应
    @Data
    @Builder
    public static class CallResult {
        private List<ContentBlock> content;
        private boolean isError;
    }

    @Data
    @Builder
    public static class ContentBlock {
        private String type;
        private String text;
    }
}
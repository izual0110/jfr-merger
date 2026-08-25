package com.example.mcpserver.config;

import com.example.mcpserver.service.HeapDumpMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

    @Bean
    ToolCallbackProvider toolCallbackProvider(HeapDumpMcpTools heapDumpMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(heapDumpMcpTools)
                .build();
    }
}

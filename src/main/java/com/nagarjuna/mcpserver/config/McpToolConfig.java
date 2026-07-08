package com.nagarjuna.mcpserver.config;

import com.nagarjuna.mcpserver.service.TaskMcpService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider taskTools(TaskMcpService taskMcpService) {

        return MethodToolCallbackProvider
                .builder()
                .toolObjects(taskMcpService)
                .build();
    }
}

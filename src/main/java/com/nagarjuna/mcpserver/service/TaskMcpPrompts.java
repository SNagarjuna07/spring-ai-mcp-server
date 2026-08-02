package com.nagarjuna.mcpserver.service;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskMcpPrompts {

    // Zero-arg prompt template: reusable "generate a standup update" ask,
    // client fetches this instead of the user typing the instruction each time.
    @McpPrompt(
            name = "daily-standup",
            description = "Generate a standup summary from current tasks"
    )
    public GetPromptResult dailyStandup() {

        String message = "Summarize pending vs completed tasks for a daily standup update. " +
                "Use the tasks://pending resource and searchByKeyword/listPendingTasks tools as needed.";

        return GetPromptResult.builder(List.of(
                new PromptMessage(Role.ASSISTANT, TextContent.builder(message).build())
        )).description("Standup Summary").build();
    }

    // Argument-taking prompt: "keyword" arg is what TaskMcpCompletions below
    // provides autocomplete suggestions for.
    @McpPrompt(
            name = "task-lookup",
            description = "Look up tasks matching a keyword"
    )
    public GetPromptResult taskLookup(
            @McpArg(name = "keyword", description = "Keyword to search task titles", required = true)
            String keyword
    ) {

        String message = "Find and summarize all tasks matching keyword: " + keyword;

        return GetPromptResult
                .builder(
                        List.of(
                                new PromptMessage(Role.ASSISTANT, TextContent.builder(message).build()
                                )
                        )
                )
                .description("Task Lookup").build();
    }
}
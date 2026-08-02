package com.nagarjuna.mcpserver.service;

import tools.jackson.databind.json.JsonMapper;
import com.nagarjuna.mcpserver.entity.Task;
import com.nagarjuna.mcpserver.repository.TaskRepository;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TaskMcpResources {

    private final TaskRepository taskRepository;

    private final JsonMapper jsonMapper;

    // Static resource: whole pending list as one JSON document. Separate
    // from the listPendingTasks TOOL on purpose - resources are for clients
    // to browse/attach context, tools are for the model to actively invoke.
    @McpResource(
            uri = "tasks://pending",
            name = "Pending Tasks",
            title = "Pending Task List",
            description = "Current pending (not completed) tasks as JSON",
            mimeType = "application/json"
    )
    public ReadResourceResult pendingTasksResource() throws Exception {

        String json = jsonMapper
                .writeValueAsString(
                        taskRepository.findByCompletedFalse()
                );

        return ReadResourceResult.builder(
                        List.of(
                                new TextResourceContents("tasks://pending", "application/json", json)
                        )
                )
                .build();
    }

    // Templated resource: single task by id, URI variable {id} bound to
    // method param by name. 404-style message returned as content, not an
    // exception, so client gets a clean read instead of a protocol error.
    @McpResource(
            uri = "task://{id}",
            name = "Task",
            title = "Single Task",
            description = "A single task by id",
            mimeType = "application/json"
    )
    public ReadResourceResult taskById(String id) throws Exception {

        Task task = taskRepository.findById(Long.valueOf(id)).orElse(null);

        String json = task != null
                ? jsonMapper.writeValueAsString(task)
                : "{\"error\":\"no task with id " + id + "\"}";

        return ReadResourceResult.builder(
                        List.of(
                                new TextResourceContents("task://" + id, "application/json", json)
                        )
                )
                .build();
    }
}
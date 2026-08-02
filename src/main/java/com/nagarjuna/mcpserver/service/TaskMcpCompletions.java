package com.nagarjuna.mcpserver.service;

import com.nagarjuna.mcpserver.entity.Task;
import com.nagarjuna.mcpserver.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TaskMcpCompletions {

    private final TaskRepository taskRepository;

    // Completes the "keyword" arg of the task-lookup prompt - client shows
    // these as the user types, backed by real DB titles instead of a static list.
    @McpComplete(prompt = "task-lookup")
    public List<String> completeKeyword(String prefix) {

        return taskRepository.findByTitleContainingIgnoreCase(prefix)
                .stream()
                .map(Task::getTitle)
                .distinct()
                .limit(10)
                .toList();
    }

    // Completes the {id} URI variable on the task:// resource template -
    // client can browse valid ids without guessing.
    @McpComplete(uri = "task://{id}")
    public List<String> completeTaskId(String prefix) {

        return taskRepository.findAll()
                .stream()
                .map(t -> String.valueOf(t.getId()))
                .filter(id -> id.startsWith(prefix))
                .limit(10)
                .toList();
    }
}
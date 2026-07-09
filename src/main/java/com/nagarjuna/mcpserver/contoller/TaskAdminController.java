package com.nagarjuna.mcpserver.contoller;

import com.nagarjuna.mcpserver.entity.Task;
import com.nagarjuna.mcpserver.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskAdminController {

    private final TaskRepository taskRepository;

    // NOT a @Tool. No LLM/MCP client can reach this. Human-confirmed only.
    @DeleteMapping("/confirm-delete")
    public ResponseEntity<Map<String, Object>> confirmDeleteCompleted() {

        List<Task> completed = taskRepository.findByCompletedTrue();

        int count = completed.size();

        taskRepository.deleteAll(completed);

        return ResponseEntity
                .ok(
                        Map.of(
                                "deletedCount", count,
                                "message", "Completed tasks permanently deleted."
                        )
                );
    }
}
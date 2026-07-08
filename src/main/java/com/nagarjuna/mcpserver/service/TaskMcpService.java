package com.nagarjuna.mcpserver.service;

import com.nagarjuna.mcpserver.entity.Task;
import com.nagarjuna.mcpserver.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskMcpService {

    private final TaskRepository taskRepository;

    // Creates and persists a new task. Description is optional - MCP clients
    // may omit it entirely, so no null-check needed before saving.
    @Tool(description = "Create a new task with a title and optional description. " +
            "Call ONLY when the user explicitly asks to add/create a task.")
    public Task createTask(
            @ToolParam(description = "Short title of the task")
            String title,
            @ToolParam(description = "Optional longer description", required = false)
            String description
    ) {

        Task task = new Task();

        task.setTitle(title);
        task.setDescription(description);

        return taskRepository.save(task);
    }

    // Returns every task not yet marked complete. No arguments needed
    // the model can call this directly without asking the user for input.
    @Tool(description = "List all pending (not completed) tasks. " +
            "Call ONLY when the user asks what's pending/open/to-do.")
    public List<Task> listPendingTasks() {

        return taskRepository.findByCompletedFalse();
    }

    // Marks a pending task done via partial, case-insensitive title match.
    // Returns a plain String (not an exception) when nothing matches, so the
    // model gets a clean message to relay instead of a stack trace.
    @Tool(description = "Mark a pending task as complete by matching its title (partial match, case-insensitive). " +
            "ONLY call this when the user explicitly asks to complete/finish/mark-done a specific task. " +
            "If multiple pending tasks match, the oldest one is completed and returned.")
    public Object completeTask(
            @ToolParam(description = "Title or partial title of the task to complete (case-insensitive")
            String title
    ) {

        List<Task> matches = taskRepository.findByTitleContainingIgnoreCaseAndCompletedFalse(title);

        if (matches.isEmpty()) {

            return "No pending task found with title: " + title;
        }

        // Extract oldest of multiple match wins - avoids NonUniqueResultException
        Task target = matches
                .stream()
                .min(
                        Comparator.comparing(
                                Task::getCreatedAt
                        )
                )
                .orElseThrow();

        target.setCompleted(true);

        return taskRepository.save(target);
    }

    // Searches both pending and completed tasks by a title keyword. Return
    // type is Object (not List<Task>) so the empty-result case can return a
    // human-readable String instead of a bare empty list.
    @Tool(description = "Search all tasks (pending and completed) by a keyword in the title. " +
            "Call ONLY when the user explicitly asks to search or find a task.")
    public Object searchByKeyword(
            @ToolParam(description = "Keyword to search for in task titles")
            String keyword
    ) {

        List<Task> results = taskRepository.findByTitleContainingIgnoreCase(keyword);

        return results.isEmpty()
                ? "No task found matching keyword: " + keyword
                : results;
    }

    // NOTE: no deleteTask / delete tool exists here on purpose.
    // Destructive ops never get an @Tool method - human-in-the-loop pattern.
    // This method only REPORTS what would be deleted; the model has no way
    // to trigger the actual delete. Real deletion lives behind a separate
    // plain REST endpoint (TaskAdminController) with zero MCP/LLM exposure.
    @Tool(description = "Report what completed tasks WOULD be deleted, without deleting anything. " +
            "This never performs the deletion itself - a human must confirm via a separate REST endpoint.")
    public String processDeleteCompleted() {

        List<Task> completed = taskRepository.findByCompletedTrue();

        if (completed.isEmpty()) {

            return "No completed tasks to delete";
        }

        String titles = completed
                .stream()
                .map(Task::getTitle)
                .reduce((a, b) ->
                        a + ", " + b)
                .orElse("");

        return "Would delete "
                + completed.size()
                + " completed tasks "
                + titles
                + ". To proceed, call DELETE /api/v1/tasks/confirm-delete directly - not available via this tool.";
    }
}
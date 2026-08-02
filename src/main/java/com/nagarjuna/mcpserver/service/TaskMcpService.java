package com.nagarjuna.mcpserver.service;

import com.nagarjuna.mcpserver.entity.Task;
import com.nagarjuna.mcpserver.repository.TaskRepository;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskMcpService {

    private final TaskRepository taskRepository;

    // Create tool: mutating but non-destructive, never idempotent (each call
    // makes a new row even with identical args). Hints tell clients this is
    // safe to surface but shouldn't be treated as a no-op retry.
    @McpTool(
            description = "Create a new task with a title and optional description. " +
                    "Call ONLY when the user explicitly asks to add/create a task.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false
            )
    )
    public Task createTask(
            McpSyncRequestContext context,
            @McpToolParam(description = "Short title of the task")
            String title,
            @McpToolParam(description = "Optional longer description", required = false)
            String description
    ) {

        // Logging notification - visible in client logs, no
        // schema pollution since context isn't a declared tool parameter.
        context.info("Creating task: " + title);

        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);

        Task saved = taskRepository.save(task);

        context.info("Task saved id: " + saved.getId());

        return saved;
    }

    // Read tool: pure query, no side effects, safe to retry/cache. Hints
    // let clients render this differently from create/complete in their UI.
    @McpTool(
            description = "List all pending (not completed) tasks. " +
                    "Call ONLY when the user asks what's pending/open/to-do.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true
            )
    )
    public List<Task> listPendingTasks() {

        return taskRepository.findByCompletedFalse();
    }

    // Complete tool: mutating, non-destructive, idempotent (completing an
    // already-completed task changes nothing further). Elicitation added -
    // when multiple pending tasks share a title fragment, ask the human via
    // the client instead of silently guessing "oldest wins".
    @McpTool(
            description = "Mark a pending task as complete by matching its title (partial match, case-insensitive). " +
                    "ONLY call this when the user explicitly asks to complete/finish/mark-done a specific task.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true
            )
    )
    public Object completeTask(
            McpSyncRequestContext context,
            @McpToolParam(description = "Title or partial title of the task to complete (case-insensitive)")
            String title
    ) {

        List<Task> matches = taskRepository
                .findByTitleContainingIgnoreCaseAndCompletedFalse(title);

        if (matches.isEmpty()) {
            return "No pending task found with title: " + title;
        }

        Task target = matches.getFirst();

        // Ambiguous match + client supports elicitation - ask human which
        // task they meant instead of guessing. Falls back to oldest-wins
        if (matches.size() > 1) {

            if (context.elicitEnabled()) {

                context.info("Multiple matches for '" + title + "', requesting clarification");

                var elicitResult = context.elicit(TaskChoice.class);

                if (elicitResult.action() == McpSchema.ElicitResult.Action.ACCEPT) {

                    TaskChoice choice = elicitResult.structuredContent();

                    target = matches.stream()
                            .filter(t -> t.getId().equals(choice.taskId()))
                            .findFirst()
                            .orElse(matches.get(0));
                } else {

                    return "Completion cancelled by user.";
                }

            } else {

                target = matches.stream()
                        .min(Comparator.comparing(Task::getCreatedAt))
                        .orElseThrow();
            }
        }

        target.setCompleted(true);

        return taskRepository.save(target);
    }

    // Record shape for the elicitation form above - client renders this as
    public record TaskChoice(Long taskId) { }

    // Search tool: read-only, idempotent. Sampling added - asks the client's
    // LLM to produce a one-line natural-language summary of raw DB results
    // server-side, so the calling model gets a pre-digested answer instead
    // of raw rows every time.
    @McpTool(
            description = "Search all tasks (pending and completed) by a keyword in the title. " +
                    "Call ONLY when the user explicitly asks to search or find a task.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true
            )
    )
    public Object searchByKeyword(
            McpSyncRequestContext context,
            @McpToolParam(description = "Keyword to search for in task titles")
            String keyword
    ) {

        List<Task> results = taskRepository.findByTitleContainingIgnoreCase(keyword);

        if (results.isEmpty()) {
            return "No task found matching keyword: " + keyword;
        }

        // Only sample when client declares the capability - keeps this tool
        // usable on clients that don't support server-initiated LLM calls.
        if (context.sampleEnabled()) {

            String titles = results
                    .stream()
                    .map(Task::getTitle)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            var samplingResult = context.sample("Summarize these matching tasks in one short sentence: " + titles);

            context.info("Sampling summary generated for keyword: " + keyword);

            return results + "\nSummary: " + samplingResult;
        }

        return results;
    }

    // Roots demo tool: lists directories/workspaces the client has exposed.
    // Not task-domain logic - included to show roots() usage; real use case
    // would be e.g. attaching a task to a project folder the client offers.
    @McpTool(
            description = "List root directories/workspaces exposed by the connecting client.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false)
    )
    public String listClientRoots(McpSyncRequestContext context) {

        if (!context.rootsEnabled()) {
            return "Client does not support roots.";
        }

        var roots = context.roots();

        StringBuilder sb = new StringBuilder();

        roots
                .roots()
                .forEach(root -> sb.append(root.uri()).append("\n"));

        return sb.isEmpty() ? "No roots exposed by client." : sb.toString();
    }

    // NOTE: no deleteTask tool exists here on purpose.
    // Destructive ops never get an @McpTool method - human-in-the-loop pattern.
    // Report-only; real deletion lives behind TaskAdminController REST endpoint.
    @McpTool(
            description = "Report what completed tasks WOULD be deleted, without deleting anything. " +
                    "This never performs the deletion itself - a human must confirm via a separate REST endpoint.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true
            )
    )
    public String processDeleteCompleted() {

        List<Task> completed = taskRepository.findByCompletedTrue();

        if (completed.isEmpty()) {
            return "No completed tasks to delete";
        }

        String titles = completed.stream().map(Task::getTitle).reduce((a, b) -> a + ", " + b).orElse("");

        return "Would delete " + completed.size() + " completed tasks " + titles
                + ". To proceed, call DELETE /api/v1/tasks/confirm-delete directly - not available via this tool.";
    }
}
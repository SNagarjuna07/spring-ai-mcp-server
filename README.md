<div align="center">

# 🔌 Kai's Task MCP Server

### Turning a Spring Boot service into a full MCP surface - tools, resources, prompts, and completions

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0-6DB33F)](https://spring.io/projects/spring-ai)
[![MCP](https://img.shields.io/badge/Protocol-MCP-blueviolet)](https://modelcontextprotocol.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

</div>

---

## 🎥 Demo - Claude Desktop Managing Real Tasks Over MCP


![Claude MCP demo](docs/Claude%20MCP%20demo.gif)

---

## 🧠 The Idea in One Line

Every other project in Spring AI taught an AI model to **talk**. This one teaches something different: how to make a piece of *your own backend* something an AI model can **use** - without writing a single line of custom integration code for each AI client that wants to use it.

That's the entire point of the **Model Context Protocol (MCP)**: a standard contract so any MCP-compatible client (Claude Desktop, an IDE agent, a custom orchestrator) can discover and call tools exposed by any MCP-compatible server - no bespoke glue code per pairing.

This project takes the task-management logic from project **[Kai](https://github.com/SNagarjuna07/spring-ai-task-manager)** and re-exposes it as a **standalone MCP server** - but goes further than a tool-only server. It implements **all four MCP primitives** (tools, resources, prompts, completions) plus all three **bidirectional server-to-client operations** (elicitation, sampling, roots), using Spring AI's native MCP annotation model. No `ChatClient`. No LLM call of its own for the core domain logic. Just annotated Java methods any AI agent can discover, read, and be guided through.

---

## 🏗️ Architecture

```
┌─────────────────────┐          MCP Protocol           ┌──────────────────────────┐
│   Claude Desktop      │◄──── Streamable HTTP (SYNC) ──►│  Spring AI MCP Server    │
│   (via mcp-remote     │       JSON-RPC 2.0 · /mcp       │  spring-ai-mcp-server    │
│    local bridge)      │                                 │  port 8082               │
└─────────────────────┘                                  └────────────┬─────────────┘
                                                                       │
                                                    annotation-scanner auto-registers
                                                    @McpTool / @McpResource / @McpPrompt
                                                    / @McpComplete beans - no manual
                                                    ToolCallbackProvider wiring
                                                                       │
                                       ┌───────────────────────────────┼────────────────────────────────┐
                                       │                               │                                │
                              ┌────────▼────────┐           ┌─────────▼─────────┐          ┌────────────▼───────────┐
                              │ TaskMcpService   │           │ TaskMcpResources   │          │ TaskMcpPrompts /       │
                              │ @McpTool methods │           │ @McpResource       │          │ TaskMcpCompletions     │
                              │ + hints, context,│           │ tasks://pending    │          │ daily-standup,         │
                              │ elicit, sample,  │           │ task://{id}        │          │ task-lookup + arg/uri  │
                              │ roots            │           │                    │          │ autocomplete           │
                              └────────┬─────────┘           └─────────┬──────────┘          └────────────┬───────────┘
                                       │                               │                                   │
                                       └───────────────────────────────┴───────────────────────────────────┘
                                                                       │
                                                              Spring Data JPA
                                                                       │
                                                           ┌───────────▼───────────┐
                                                           │   PostgreSQL           │
                                                           │   tasks table          │
                                                           └────────────────────────┘

               ⚠️  One path deliberately has NO protocol access:

┌─────────────────────┐    Plain REST — human only    ┌──────────────────────────┐
│   You (curl/Postman) │───────────────────────────────►│ DELETE /api/v1/tasks/    │
│   Not an AI client   │      TaskAdminController        │        confirm-delete   │
└─────────────────────┘                                  └──────────────────────────┘
```

---

## 🔧 The Full MCP Surface

MCP defines four primitives. Most portfolio MCP servers stop at tools. This one implements all four, plus the three server-initiated operations the spec allows mid-request.

**Mental model:** Tools = verbs the model can invoke · Resources = nouns the client can read for context · Prompts = canned instruction templates the user picks · Completions = autocomplete on a prompt's blanks or a resource's URI variables.

### Tools (`TaskMcpService`)

| Tool | What it does | Hints |
|---|---|---|
| `createTask` | Creates a task with title + optional description | mutating, not idempotent |
| `listPendingTasks` | Lists all incomplete tasks | read-only, idempotent |
| `completeTask` | Marks a task done via partial title match; **elicits** the human for disambiguation on multiple matches, falls back to oldest-wins if the client can't elicit | mutating, idempotent |
| `searchByKeyword` | Finds tasks by title keyword; **samples** the client's own LLM for a one-line summary when supported | read-only, idempotent |
| `listClientRoots` | Reads the client's exposed **roots** (workspaces/folders) | read-only |
| `processDeleteCompleted` | **Reports** what completed tasks *would* be deleted | read-only, idempotent, `destructiveHint=false` (never deletes) |

Every tool declares `readOnlyHint` / `destructiveHint` / `idempotentHint` - not enforced by the runtime, but a truthful, client-facing contract about what each call actually does, so a client can decide what to auto-approve versus confirm with a human.

Notice what's missing: there is no `deleteTask` tool. That's not an oversight.

### Resources (`TaskMcpResources`)

| Resource URI | Returns |
|---|---|
| `tasks://pending` | Full pending-task list as JSON |
| `task://{id}` | A single task by id (templated URI) |

Separate from `listPendingTasks` on purpose - resources are for a client to browse or attach as context, not something the model is expected to actively decide to invoke.

### Prompts (`TaskMcpPrompts`)

| Prompt | Purpose |
|---|---|
| `daily-standup` | Zero-arg template: generate a pending-vs-completed summary |
| `task-lookup` | Takes a `keyword` arg: find and summarize matching tasks |

### Completions (`TaskMcpCompletions`)

| Completes | Source |
|---|---|
| `task-lookup`'s `keyword` argument | Live task titles from Postgres |
| `task://{id}`'s `{id}` URI variable | Live task ids from Postgres |

### Bidirectional operations

- **Elicitation** - `completeTask` pauses mid-call to ask the *human*, through the client's UI, which task was meant when a title match is ambiguous. Different from the delete-gating below: this is human-in-the-loop *during* execution, not instead of exposing the action at all.
- **Sampling** - `searchByKeyword` asks the *client's* LLM to summarize results server-side. This server holds no LLM API key of its own for this - it borrows the calling client's model.
- **Roots** - `listClientRoots` reads workspace/folder context the client has exposed, demonstrating the primitive even though this domain (tasks in Postgres) has no direct filesystem use for it.
- **Request context logging** - `createTask`, `completeTask`, and `searchByKeyword` all emit `context.info(...)` notifications mid-call, visible live in MCP Inspector / client logs, without polluting the tool's JSON schema.

All of the above are guarded with `context.elicitEnabled()` / `context.sampleEnabled()` / `context.rootsEnabled()` checks, since not every connecting client supports them - the server degrades gracefully instead of failing on non-interactive callers.

---

## 🛡️ Design Decision: AI Doesn't Get the Delete Button

This rule is applied on purpose:

> **Destructive operations are never exposed as tools an AI model can call directly.**

`processDeleteCompleted` lets the model *reason* about what deletion would do and *tell the user* - but the actual `DELETE /api/v1/tasks/confirm-delete` endpoint has zero MCP exposure. It's a plain REST call a human has to make deliberately, outside the protocol entirely - a completely separate call path from the MCP tool layer, not just a permissions check bolted onto the same method.

Elicitation in `completeTask` is a related but distinct idea worth calling out explicitly: that's human-in-the-loop *inside* an allowed action (pause and ask when ambiguous), while delete-gating is human-in-the-loop *by never allowing the action onto the tool surface at all*. Two different trust mechanisms, same underlying belief: **an LLM being confident about an action is not the same thing as that action being authorized.**

Repeating this pattern across two independent projects - one built with direct tool-calling, one built on an entirely different protocol - is meant to show it's an actual engineering principle, not something that happened to work once.

---

## 💡 What This Project Demonstrates

- **Full MCP primitive coverage** - tools, resources, prompts, and completions in one server, not just a tool-calling wrapper
- **Server-initiated bidirectional operations** - elicitation, sampling, and roots, using Spring AI's native `McpSyncRequestContext`, not just request/response tool calls
- **Tool trust metadata** - `readOnlyHint` / `destructiveHint` / `idempotentHint` on every tool, a declared contract clients can act on
- **MCP server implementation** using `spring-ai-starter-mcp-server-webmvc` on the **Streamable HTTP** transport, stateful (`SYNC`) session mode - required for elicit/sample/roots to function at all
- **Protocol-first tool design** - descriptions written for *any* MCP client to correctly infer intent, not tuned to one model's quirks
- **Reuse across architectural styles** - same underlying task domain (**[Kai](https://github.com/SNagarjuna07/spring-ai-task-manager)**) re-exposed through a completely different access pattern, with the safety boundary preserved
- **Real client integration** - connects to and is driven by Claude Desktop, not a custom-built test harness
- **Protocol-level manual testing** - verified the raw JSON-RPC handshake (`initialize` → `notifications/initialized` → `tools/list` → `tools/call`) directly in Postman before ever touching Claude Desktop, isolating protocol bugs from client bugs

---

## 📁 Project Structure

```
spring-ai-mcp-server/
├── src/main/java/com/nagarjuna/mcpserver/
│   ├── SpringAiMcpServerApplication.java
│   ├── entity/Task.java
│   ├── repository/TaskRepository.java
│   ├── service/
│   │   ├── TaskMcpService.java       ← tools: create/list/complete/search/roots/report-delete
│   │   ├── TaskMcpResources.java     ← @McpResource: tasks://pending, task://{id}
│   │   ├── TaskMcpPrompts.java       ← @McpPrompt: daily-standup, task-lookup
│   │   └── TaskMcpCompletions.java   ← @McpComplete: keyword + id autocomplete
│   ├── contoller/TaskAdminController.java   ← human-only REST delete, no MCP access
│   └── exception/GlobalExceptionHandler.java
├── src/main/resources/application.yaml
├── compose.yaml                  ← local dev, Postgres only, auto-managed by Boot
├── docker-compose.full.yml       ← full containerized run
├── Dockerfile
└── .env                          ← gitignored
```

No manual tool-registration config class - `spring.ai.mcp.server.annotation-scanner.enabled: true` auto-detects every `@McpTool` / `@McpResource` / `@McpPrompt` / `@McpComplete` bean on startup.

---

## 🚀 Running It

### Option A - IntelliJ + auto-managed Postgres (recommended for dev)
```bash
# Boot auto-starts compose.yaml (Postgres only) when the app runs
./mvnw spring-boot:run
```

### Option B - Fully containerized
```bash
docker compose -f docker-compose.full.yml up --build
```

Server starts on **`http://localhost:8082`**, MCP endpoint at **`/mcp`**.

Critical config - without this exact block, the `/mcp` endpoint is never mapped and every request 404s as a static resource lookup instead of hitting the protocol handler; `type: SYNC` is required for elicit/sample/roots to work at all:

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        name: kai-task-mcp-server
        version: 1.0.0
        type: SYNC
        annotation-scanner:
          enabled: true
```

---

## 🧪 Testing the Protocol Directly (Postman)

Before wiring any AI client, the MCP handshake was verified manually - Streamable HTTP is stateful JSON-RPC 2.0 over a single POST endpoint, testable like any other API:

1. **`initialize`** - POST to `/mcp` with `Accept: application/json, text/event-stream`. Response header carries `Mcp-Session-Id`.
2. **`notifications/initialized`** - required handshake step, include the session header, expect `202 Accepted`.
3. **`tools/list`** - confirms all tools register with correct auto-generated JSON schemas and hints.
4. **`resources/list`** / **`prompts/list`** / **`completion/complete`** - confirms the non-tool primitives register too.
5. **`tools/call`** - invoke a tool directly (e.g. `createTask`) and confirm the row lands in Postgres independently of the protocol response.

This step caught real bugs before they ever reached Claude Desktop: a missing `protocol: STREAMABLE` property, and a duplicate-`Accept`-header issue caused by Postman's auto-generated default headers overriding the manually-set one.

---

### Connecting Claude Desktop

Claude Desktop's config file speaks stdio, not raw HTTP - so a local bridge (`mcp-remote`) is required to forward stdio traffic to the Streamable HTTP endpoint. Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "kai-task-mcp-server": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://localhost:8082/mcp",
        "--transport", "http-only",
        "--allow-http"
      ]
    }
  }
}
```

`--transport http-only` and `--allow-http` skip SSE-fallback probing and OAuth-discovery attempts that are irrelevant for a local, unauthenticated dev server.

Windows config path: `%APPDATA%\Claude\claude_desktop_config.json`
Requires Node.js (`npx` on PATH).

Fully quit and restart Claude Desktop after editing. The task tools, resources, and prompts then appear as available capabilities - ask something like *"what's still pending on my task list?"* and watch it call your server, or *"complete the report task"* when multiple matches exist and watch it ask you which one via elicitation.

---

## 🐘 Database

Reuses the same `tasks` table shape as Project **[Kai](https://github.com/SNagarjuna07/spring-ai-task-manager)**. One Postgres instance, one schema, no vector store or chat memory tables involved this time - this project has nothing to do with embeddings or conversation history, deliberately.

---

## 🔭 Not Yet Done

- STDIO transport variant (currently Streamable HTTP only)
- Stateless server mode as an explicit alternative (would simplify scaling but drop elicit/sample/roots support - documented as a known trade-off, not pursued here since demonstrating the bidirectional operations was the point)
- MCP client module to consume *external* MCP servers from this same codebase
- Automated integration tests against the MCP protocol layer
- MCP Security / OAuth2 resource-server auth for public exposure - out of scope for this local-dev-focused iteration, natural next hardening step once the module stabilizes

---


<div align="center">

Built by **S Nagarjuna**
</div>
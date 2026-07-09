<div align="center">

# 🔌 Kai's Task MCP Server

### Turning a Spring Boot service into a tool Claude can call directly

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M6-6DB33F)](https://spring.io/projects/spring-ai)
[![MCP](https://img.shields.io/badge/Protocol-MCP-blueviolet)](https://modelcontextprotocol.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

</div>

---
## 🎥 Demo - Claude Desktop Managing Real Tasks Over MCP


https://github.com/user-attachments/assets/b1cc89af-f5d4-4cb1-9421-e96aba196033

---

## 🧠 The Idea in One Line

Every other project in this portfolio taught an AI model to **talk**. This one teaches something different: how to make a piece of *your own backend* something an AI model can **use** - without writing a single line of custom integration code for each AI client that wants to use it.

That's the entire point of the **Model Context Protocol (MCP)**: a standard contract so any MCP-compatible client (Claude Desktop, an IDE agent, a custom orchestrator) can discover and call tools exposed by any MCP-compatible server - no bespoke glue code per pairing.

This project takes the task-management logic from project **[Kai](https://github.com/SNagarjuna07/spring-ai-task-manager)** and re-exposes it as a **standalone MCP server**. No `ChatClient`. No LLM call inside this codebase at all. Just clean, well-described Java methods that any AI agent can discover and invoke over HTTP.

---

## 🏗️ Architecture

```
┌─────────────────────┐          MCP Protocol           ┌──────────────────────────┐
│   Claude Desktop     │◄──── Streamable HTTP / SSE ────►│  Spring AI MCP Server    │
│   (via mcp-remote     │       JSON-RPC 2.0 · /mcp       │  spring-ai-mcp-server    │
│    local bridge)      │                                 │  port 8082               │
└─────────────────────┘                                  └────────────┬─────────────┘
                                                                       │
                                                          ToolCallbackProvider
                                                          (McpToolConfig)
                                                                       │
                                                           ┌───────────▼───────────┐
                                                           │   TaskMcpService       │
                                                           │  @Tool-annotated       │
                                                           │  Java methods          │
                                                           └───────────┬───────────┘
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
└─────────────────────┘      → TaskAdminService          └──────────────────────────┘
```

---

## 🔧 Exposed MCP Tools

| Tool | What it does | Guardrail                                                                                                             |
|---|---|-----------------------------------------------------------------------------------------------------------------------|
| `createTask` | Creates a task with title + optional description | -                                                                                                                     |
| `listPendingTasks` | Lists all incomplete tasks | -                                                                                                                     |
| `completeTask` | Marks a task done via partial title match | Scoped to pending tasks only; oldest match wins on ambiguity, avoiding `NonUniqueResultException` on duplicate titles |
| `searchByKeyword` | Finds tasks (pending or done) by title keyword | Handles zero-result case explicitly, returns a message instead of an empty list                                       |
| `processDeleteCompleted` | **Reports** what completed tasks *would* be deleted | **Never deletes.** Just describes the action and points to the human-only endpoint                                    |

Notice what's missing: there is no `deleteTask` tool. That's not an oversight.

---

## 🛡️ Design Decision: AI Doesn't Get the Delete Button

This  rule is applied on purpose:

> **Destructive operations are never exposed as tools an AI model can call directly.**

`processDeleteCompleted` lets the model *reason* about what deletion would do and *tell the user* - but the actual `DELETE /api/v1/tasks/confirm-delete` endpoint has zero MCP exposure. It's a plain REST call a human has to make deliberately, outside the protocol entirely, a completely separate call path from the MCP tool layer, not just a permissions check bolted onto the same method.

Repeating this pattern across two independent projects - one built with direct tool-calling, one built on an entirely different protocol - is meant to show it's an actual engineering principle, not something that happened to work once. The underlying belief: **an LLM being confident about an action is not the same thing as that action being authorized.**

---

## 💡 What This Project Demonstrates

- **MCP server implementation** using `spring-ai-starter-mcp-server-webmvc` with the **Streamable HTTP** transport - the current MCP standard, replacing the older SSE-only approach
- **Protocol-first tool design** - tool descriptions written for *any* MCP client to correctly infer intent, not tuned to one model's quirks
- **Reuse across architectural styles** - same underlying task domain (**[Kai](https://github.com/SNagarjuna07/spring-ai-task-manager)**) re-exposed through a completely different access pattern, with the safety boundary preserved
- **Real client integration** - connects to and is driven by Claude Desktop, not a custom-built test harness
- **Protocol-level manual testing** - verified the raw JSON-RPC handshake (`initialize` → `notifications/initialized` → `tools/list` → `tools/call`) directly in Postman before ever touching Claude Desktop, isolating protocol bugs from client bugs

---

## 📁 Project Structure

```
spring-ai-mcp-server/
├── src/main/java/com/nagarjuna/mcpserver/
│   ├── McpServerApplication.java
│   ├── entity/Task.java
│   ├── repository/TaskRepository.java
│   ├── service/
│   │   ├── TaskMcpService.java        ← the 5 @Tool methods, AI-reachable
│   │   └── TaskAdminService.java      ← plain service, human-only, never a @Tool
│   ├── config/McpToolConfig.java      ← registers TaskMcpService's tools with the MCP server
│   ├── controller/TaskAdminController.java  ← human-only REST, no MCP access
│   └── exception/GlobalExceptionHandler.java
├── src/main/resources/application.yml
├── compose.yml                  ← local dev, Postgres only, auto-managed by Boot
├── docker-compose.full.yml      ← full containerized run
├── Dockerfile
└── .env                         ← gitignored
```

---

## 🚀 Running It

### Option A - IntelliJ + auto-managed Postgres (recommended for dev)
```bash
# Boot auto-starts compose.yml (Postgres only) when the app runs
./mvnw spring-boot:run
```

### Option B - Fully containerized
```bash
docker compose -f docker-compose.full.yml up --build
```

Server starts on **`http://localhost:8082`**, MCP endpoint at **`/mcp`**.

Critical config - without this exact property, the `/mcp` endpoint is never mapped and every request 404s as a static resource lookup instead of hitting the protocol handler:

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        name: kai-task-mcp-server
        version: 1.0.0
```

---

## 🧪 Testing the Protocol Directly (Postman)

Before wiring any AI client, the MCP handshake was verified manually - Streamable HTTP is stateful JSON-RPC 2.0 over a single POST endpoint, testable like any other API:

1. **`initialize`** - POST to `/mcp` with `Accept: application/json, text/event-stream`. Response header carries `Mcp-Session-Id`.
2. **`notifications/initialized`** - required handshake step, include the session header, expect `202 Accepted`.
3. **`tools/list`** - confirms all 5 tools register with correct auto-generated JSON schemas.
4. **`tools/call`** - invoke a tool directly (e.g. `createTask`) and confirm the row lands in Postgres independently of the protocol response.

This step caught two real bugs before they ever reached Claude Desktop: a missing `protocol: STREAMABLE` property, and a duplicate-`Accept`-header issue caused by Postman's auto-generated default headers overriding the manually-set one.

---

### Connecting Claude Desktop

Claude Desktop's config file speaks stdio, not raw HTTP — so a local bridge (`mcp-remote`) is required to forward stdio traffic to the Streamable HTTP endpoint. Add to `claude_desktop_config.json`:

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

Fully quit and restart Claude Desktop after editing. The task tools then appear as available capabilities - ask something like *"what's still pending on my task list?"* and watch it call your server.

---

## 🐘 Database

Reuses the same `tasks` table shape as Project **[Kai](https://github.com/SNagarjuna07/spring-ai-task-manager)**. One Postgres instance, one schema, no vector store or chat memory tables involved this time - this project has nothing to do with embeddings or conversation history, deliberately.

---

## 🔭 Not Yet Done

- STDIO transport variant (currently Streamable HTTP only)
- MCP client module to consume *external* MCP servers from this same codebase
- Automated integration tests against the MCP protocol layer
- Deployed public instance with proper auth - MCP servers exposed to the public internet need a real auth layer (OAuth2/Bearer), which is out of scope for this local-dev-focused iteration

---


<div align="center">

Built by **Nagarjuna**
</div>

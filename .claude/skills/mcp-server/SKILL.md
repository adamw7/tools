---
name: mcp-server
description: Add or change an MCP tool or server on this repo's shared mcp-common scaffolding — the McpTool SPI, transport-neutral ToolDefinition/ToolResult, the three transports, path confinement, MCP_USAGE.md and the *IT tests. Use when exposing functionality over MCP, wiring a new server, or when the user says "MCP tool", "MCP server", "stdio transport", or "streamable HTTP".
---

# MCP Server Skill

Expose functionality over the Model Context Protocol using `mcp-common`, the
shared scaffolding behind all three servers here (`data` uniqueness,
`code/context`, `adopt`). Transport, registration and error handling are already
solved — a new tool writes none of that.

## When to Use
- Adding a tool to an existing MCP server, or standing a new server up
- Changing a tool's input schema or its result
- The user says "MCP tool" / "MCP server" / "stdio transport" / "streamable HTTP"

## The SPI — a tool never mentions the MCP SDK
```java
public class MyTool implements McpTool {   // Function<Map<String,Object>, ToolResult>

    @Override
    public ToolDefinition getToolDefinition() {
        return new ToolDefinition("my_tool", "What it does, for the model.",
                Map.of("type", "object",
                       "properties", Map.of("path", Map.of("type", "string")),
                       "required", List.of("path")));
    }

    @Override
    public ToolResult apply(Map<String, Object> arguments) {
        String path = ToolArguments.requiredString(arguments, "path");
        return ToolResult.success(answer);          // or ToolResult.error(message)
    }
}
```

- `ToolDefinition` (name, description, JSON-schema map) and `ToolResult`
  (`success` / `error`) are the SPI's **own transport-neutral types**;
  `AbstractMcpConfiguration` translates them into the SDK's `Tool` and
  `CallToolResult`. Do not import `io.modelcontextprotocol` from a tool.
- Parse arguments with `ToolArguments`: `requiredString`, `requiredInt`,
  `optionalString`, `optionalBoolean`, `optionalInt`, `optionalBoundedInt` —
  don't hand-roll casts out of the map.
- **Don't catch-and-wrap for the protocol's sake.** `AbstractMcpConfiguration`
  turns any thrown `RuntimeException` into an error `ToolResult` naming the
  tool, so a bad argument reaches the client as an actionable tool error. Throw
  a clear exception; return `ToolResult.error` when the failure is expected.

## The server
```java
@Configuration
public class McpConfiguration extends AbstractMcpConfiguration {
    @Override protected String serverName() { return "my-server"; }
    @Override protected List<McpTool> tools() { return List.of(new MyTool()); }
}
```
That is the whole server. The base class supplies all three transports, selected
with `--transport.mode`:

| Mode | Value | Endpoint |
|---|---|---|
| stdio (default) | `stdio` | — |
| streamable HTTP | `streamable-http` | `/mcp` |
| stateless HTTP | `stateless-http` | `/mcp`, no session kept |

A `Main.java` Spring Boot entry point sits next to the configuration
(`entryPointsAreNamedMain` is an ArchUnit rule in `adopt`).

## Rules that bite
- **Confine file access.** A server that reads paths from a client must clamp
  them to a configured base directory before returning any tool — the uniqueness
  server does this in `tools()` via `PathValidator.setAllowedBaseDir`, defaulting
  to the working directory, so a client cannot steer it at `/etc/passwd`.
- **The core must not depend on its MCP adapter.** ArchUnit pins this in every
  module: the uniqueness/context/adoption logic knows nothing about `mcp`, and
  only the `mcp` package may see Spring or the scaffolding. Put the adapter in
  `…/mcp`, keep the logic outside it.
- Log through log4j2 — no `System.out`, which would corrupt the stdio transport
  anyway.

## Document and test it
- Every server has an `MCP_USAGE.md` **next to its `mcp` package**, listing each
  tool, its parameters and example calls. Update it in the same change as the
  tool — it is the file users configure their client from.
- The servers are covered by `*IT` tests over real HTTP, gated behind the
  `integration-tests` profile: `mvn -P integration-tests verify`. The profile is
  declared **per module** (`data`, `code/context`, `adopt`) — a new module's
  `*IT`s do not run until that module gets its own copy.
- Unit-test the tool directly as a function: build the argument map, assert on
  the `ToolResult` text and `isError`.

## References
- `mcp-common/.../AbstractMcpConfiguration.java` — transports and registration
- `data/.../uniqueness/mcp/` — the smallest complete example
- `docs/adr/0009-mcp-servers-on-spring-boot.md` — why Spring Boot
- The three `MCP_USAGE.md` files — the user-facing contract

# MCP Server

**Load**: `view .claude/skills/mcp-server/SKILL.md`

---

## Description

Helps Claude add or change an MCP tool or server on the shared `mcp-common`
scaffolding: the `McpTool` SPI, transport-neutral `ToolDefinition` /
`ToolResult`, `ToolArguments` parsing, the three transports, path confinement,
the `MCP_USAGE.md` convention and the `*IT` tests.

---

## Use Cases

- "Expose the key finder over MCP"
- "Add a `format` argument to the project_tree tool"
- "Run the MCP server over HTTP instead of stdio"

---

## Examples

```
> view .claude/skills/mcp-server/SKILL.md
> "Add an estimate_rows tool to the uniqueness server"
→ implement McpTool (ToolDefinition + apply), parse with ToolArguments,
  register in McpConfiguration.tools(), update MCP_USAGE.md, add a unit test
```

---

## Notes / Tips

- A tool must not import the MCP SDK — the scaffolding translates the SPI types.
- Thrown `RuntimeException`s already become error results; don't wrap for the
  protocol.
- The `integration-tests` profile is declared per module, not in the root pom.

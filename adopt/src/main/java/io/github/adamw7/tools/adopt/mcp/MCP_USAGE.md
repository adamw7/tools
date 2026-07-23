# MCP Server for Claude Code Adoption

This directory contains a Model Context Protocol (MCP) server that exposes the
`adopt` module's pipeline as an `adopt_repo` tool, so MCP clients such as Claude
Desktop or Claude Code can adopt Claude Code into a GitHub repository on
request.

## Overview

The `adopt_repo` tool runs the same default pipeline as the command-line entry
point: it checks the required tools (`git`, `claude`, `gh`) are installed,
clones the repository, creates a feature branch, generates `CLAUDE.md` with
`claude init`, wires a build-tool-aware `CLAUDE.md` guard into the build,
verifies it, pushes the branch, and opens a pull request. The default branch is
never written to. The tool answers with a JSON report of the run: the
repository, the branch, the pull request URL, and the completed steps.

Because the pipeline shells out to `git`, `claude`, and `gh`, those tools must
be installed and authenticated on the machine running the MCP server.

## Architecture

1. **Main.java** — Spring Boot entry point that selects the transport
   (stdio by default)
2. **McpConfiguration.java** — registers the tool against the shared
   `mcp-common` scaffolding
3. **AdoptTool.java** — maps the tool arguments onto the adoption pipeline and
   renders the resulting `AdoptionReport` as JSON

The server supports the same transports as the repository's other MCP servers:
stdio (default), streamable HTTP (`--transport.mode=streamable-http`, served at
`/mcp`), stateless HTTP (`--transport.mode=stateless-http`, also at `/mcp`), and
the legacy HTTP+SSE transport (`--transport.mode=sse`, event stream at `/sse`,
messages at `/mcp/message`).

## Building the Server

From the root of the repository:

```bash
mvn clean install
```

This creates an executable JAR in `adopt/target/tools.adopt-{version}.jar`.

## Tool Specification

### adopt_repo

**Parameters:**
- `repository_url` (string, required): URL of the GitHub repository to adopt
- `workspace` (string, optional): directory to clone into; a temporary
  directory is created when omitted
- `branch` (string, optional): feature branch name; defaults to
  `claude/adopt-claude-code`
- `title` / `body` (string, optional): pull request title and body
- `reviewers` / `labels` / `assignees` (string, optional): comma-separated
  values applied to the pull request
- `draft` (boolean, optional): open the pull request as a draft
- `assets` (boolean, optional): also commit starter Claude Code configuration
  assets (`AGENTS.md`, `.claude/settings.json`, a session-start hook,
  `.mcp.json`, and an `@claude`-mention GitHub Actions workflow)

**Returns** a JSON report:

```json
{
  "repositoryUrl" : "https://github.com/owner/repo.git",
  "branch" : "claude/adopt-claude-code",
  "pullRequestUrl" : "https://github.com/owner/repo/pull/42",
  "completedSteps" : [ "toolchain", "clone", "branch", "trust", "claude-init",
    "commit", "enforcer", "commit", "verify", "push", "pull-request" ]
}
```

## Configuring MCP Clients

For any MCP client that supports stdio transport:

```json
{
  "mcpServers": {
    "claude-code-adopt": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/tools/adopt/target/tools.adopt-{version}.jar"
      ]
    }
  }
}
```

## Related Documentation

- [Uniqueness-checker MCP server](../../../../../../../../../../data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/MCP_USAGE.md)
- [Context module MCP server](../../../../../../../../../../code/context/src/main/java/io/github/adamw7/context/mcp/MCP_USAGE.md)

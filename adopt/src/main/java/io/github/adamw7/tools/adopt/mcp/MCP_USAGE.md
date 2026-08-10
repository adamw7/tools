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

One call can adopt a list of repositories, one after another, sharing the
workspace and branch name. A repository whose adoption fails does not strand the
ones behind it: the batch runs to the end and the report says which landed, the
result being marked as an error when any of them did not. Each repository claims
its checkout directory inside its own adoption, so a second repository of the list
that would clone into a directory the first already claimed is that repository's
recorded failure — never an adoption on top of the first — rather than an abort of
the whole call.

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
`/mcp`), and stateless HTTP (`--transport.mode=stateless-http`, also at `/mcp`).

## Building the Server

From the root of the repository:

```bash
mvn clean install
```

This creates an executable JAR in `adopt/target/tools.adopt-{version}.jar`.

## Tool Specification

### adopt_repo

**Parameters:**
- `repository_url` (string): URL of the GitHub repository to adopt
- `repository_urls` (array of strings, or a comma-separated string): the
  repositories to adopt in one call. At least one of `repository_url` and
  `repository_urls` must name a repository; supplying both adopts them all, once
  each
- `workspace` (string, optional): directory to clone into, shared by every
  repository of the call — each clone lands in its own directory under it, named
  after the repository, and two repositories of one call may not share that
  directory; a temporary directory is created when omitted
- `branch` (string, optional): feature branch name; defaults to
  `claude/adopt-claude-code`
- `title` / `body` (string, optional): pull request title and body
- `reviewers` / `labels` / `assignees` (comma-separated string, or an array of
  strings, optional): values applied to the pull request
- `draft` (boolean, optional): open the pull request as a draft
- `assets` (boolean, optional): also commit starter Claude Code configuration
  assets (`AGENTS.md`, `.claude/settings.json`, a session-start hook,
  `.mcp.json`, and an `@claude`-mention GitHub Actions workflow)
- `rule_version` (string, optional): the released `claude-code-enforcer` version
  to wire into an adopted Maven project; defaults to the version of the `tools`
  build running the server, and a `-SNAPSHOT` is refused either way
- `dry_run` (boolean, optional): rehearse the adoption — clone, branch, commit,
  wire in the guard, and verify it, but push nothing and open no pull request.
  The pipeline is assembled without those two steps, so `completedSteps` ends at
  `verify` and the checkout is left to be read at the `checkout` path the report
  answers with. Worth reaching for before letting a call write to GitHub
- `timeout_minutes` (integer, optional): how long any one `git`/`claude`/`gh`/build
  command may run before it is killed. Defaults to 10, and is bounded to a day —
  this server is long-lived, so a command it could never reclaim is refused

**Returns** a JSON report. Each commit the adoption makes is named for what it
commits (`commit:claude-md`, `commit:guard`, `commit:assets`), so a run that
stopped part-way says which of them landed. `checkout` is where the adoption's
working tree is — the run's one output that never reaches GitHub, and the only
way to find the temporary directory a call that named no `workspace` was given:

```json
{
  "repositoryUrl" : "https://github.com/owner/repo.git",
  "branch" : "claude/adopt-claude-code",
  "checkout" : "/tmp/claude-adopt-4711/repo",
  "pullRequestUrl" : "https://github.com/owner/repo/pull/42",
  "succeeded" : true,
  "failure" : null,
  "completedSteps" : [ "toolchain", "clone", "build-toolchain", "branch", "trust",
    "claude-init", "conform", "commit:claude-md", "enforcer", "commit:guard",
    "verify", "push", "pull-request" ]
}
```

A call that adopted several repositories answers with the batch document
instead — an overall `succeeded`, true only when every repository was adopted,
and a `repositories` array of exactly the documents above:

```json
{
  "succeeded" : false,
  "repositories" : [ {
    "repositoryUrl" : "https://github.com/owner/repo.git",
    "succeeded" : true,
    "failure" : null,
    "…" : "…"
  }, {
    "repositoryUrl" : "https://github.com/owner/other.git",
    "succeeded" : false,
    "failure" : "clone: repository not found",
    "…" : "…"
  } ]
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

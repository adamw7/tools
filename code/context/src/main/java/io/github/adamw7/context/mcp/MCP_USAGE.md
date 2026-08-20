# MCP Server for Context Engineering

This package contains a Model Context Protocol (MCP) server that exposes the
context-engineering capabilities of the `code/context` module — the project-tree
scanner and the class-usage context finder — to MCP clients such as Claude
Desktop, Cline, or any other MCP-compatible client.

## Overview

The server exposes four tools:

- **`project_tree`** — scans a Java, Kotlin or Scala project into a tree of
  folders, files and the classes each file depends on, then serialises it as JSON
  (default), Markdown, plain text, Graphviz DOT or a Mermaid flowchart.
- **`find_context`** — resolves the classes a single source file depends on,
  bounded by a configurable depth, and returns them as a JSON array.
- **`estimate_tokens`** — estimates the LLM token cost of the context assembled
  for a class (the class itself plus its dependencies), returning a per-class
  breakdown and the total.
- **`okf_bundle`** — scans the same project into a bundle in Google's
  [Open Knowledge Format](https://github.com/GoogleCloudPlatform/knowledge-catalog/blob/main/okf/SPEC.md)
  (OKF) v0.2: a directory of markdown concept documents with YAML frontmatter,
  portable to any consumer that speaks OKF.

All four resolve dependencies with the package-aware finder, which reads each
source's `package` declaration and `import` statements, so two classes sharing a
simple name in different packages are told apart rather than resolved to whichever
was scanned first.

## Architecture

The implementation lives in a package separate from the core finders:

1. **Main.java** — Spring Boot entry point that selects the transport.
2. **McpConfiguration.java** — Spring configuration that wires the transports and
   registers the tools.
3. **ContextTool.java** — the abstraction every tool implements.
4. **ProjectTreeTool.java** / **ContextFinderTool.java** / **EstimateTokensTool.java**
   / **OkfBundleTool.java** — the four tools.
5. **LanguageArguments.java** — resolves the optional `language` argument; the
   generic argument parsing is shared from `mcp-common`'s `ToolArguments`.

The server uses:

- **Transport**: stdio (default), streamable HTTP
  (`--transport.mode=streamable-http`), which serves the MCP endpoint at `/mcp`,
  or stateless HTTP (`--transport.mode=stateless-http`), which serves the same
  `/mcp` endpoint without keeping a session. Any other value is refused at
  startup with a message naming the three.
- **MCP SDK**: `io.modelcontextprotocol.sdk` v2.0.0
- **Framework**: Spring Boot
- **Protocol**: Model Context Protocol (MCP)

## Building the Server

From the root of the repository:

```bash
mvn clean install
```

This creates an executable JAR in `code/context/target/tools.code.context-{version}.jar`.

## Tool Specifications

### project_tree

Scans a project into a tree of folders, files and class dependencies.

**Parameters:**

- `path` (string, required): absolute path to the project root directory
- `language` (string, optional): `java` (default), `kotlin` or `scala`
- `depth` (integer, optional): levels of transitive dependencies to resolve, from `1` to `10` (default `1`)
- `format` (string, optional): `json` (default), `markdown`, `text`, `dot` or `mermaid`

**Example:**

```json
{
  "path": "/path/to/project",
  "language": "java",
  "depth": 2,
  "format": "json"
}
```

### find_context

Finds the classes a given class depends on, within a project.

**Parameters:**

- `path` (string, required): absolute path to the project root directory
- `class_name` (string, required): simple name of the class to inspect, e.g. `Foo` or `Foo.java`
- `language` (string, optional): `java` (default), `kotlin` or `scala`
- `depth` (integer, optional): levels of transitive dependencies to resolve, from `1` to `10` (default `1`)

**Returns:** a JSON array of dependency class names, e.g. `["A.java","B.java"]`.
An unknown class is reported as an error result.

**Example:**

```json
{
  "path": "/path/to/project",
  "class_name": "B",
  "depth": 1
}
```

### estimate_tokens

Estimates the LLM token cost of the context assembled for a class and its
dependencies, to a bounded depth.

**Parameters:**

- `path` (string, required): absolute path to the project root directory
- `class_name` (string, required): simple name of the class to inspect, e.g. `Foo` or `Foo.java`
- `language` (string, optional): `java` (default), `kotlin` or `scala`
- `depth` (integer, optional): levels of transitive dependencies to resolve, from `1` to `10` (default `1`)

**Returns:** a JSON object with the `total` token estimate and a `classes` array of
`{ "class": ..., "tokens": ... }` entries, the target class first. An unknown
class is reported as an error result.

**Example:**

```json
{
  "path": "/path/to/project",
  "class_name": "B",
  "depth": 1
}
```

### okf_bundle

Scans a project into a bundle in Google's Open Knowledge Format (OKF) v0.2.

**Parameters:**

- `path` (string, required): absolute path to the project root directory
- `language` (string, optional): `java` (default), `kotlin` or `scala`
- `depth` (integer, optional): levels of transitive dependencies to resolve, from `1` to `10` (default `1`)

**Returns:** a JSON object with the targeted `okf_version` and a `documents` map
from each bundle-relative path to that document's markdown. Every directory
becomes a reserved `index.md` listing what it holds, and every file becomes a
concept document at its own name plus `.md` — or, where that would claim a name
OKF reserves (a file called `index` or `log`), at that name plus `.concept.md`.
A concept document is YAML frontmatter naming the file, then a `# Dependencies`
section linking to the concepts it relies on. The bundle is **returned, never written**:
the server stays read-only, so a client cannot use it to create files on the
host. Write it out with `OkfBundleWriter` on the consumer's side.

**Example:**

```json
{
  "path": "/path/to/project",
  "language": "java",
  "depth": 1
}
```

A returned concept document looks like this:

```markdown
---
type: "Java Source File"
title: "B.java"
description: "Java source file with 1 project dependency."
resource: "pkg/B.java"
tags: ["source", "java"]
generated: { by: "tools.code.context/1", at: "2026-08-03T10:15:30Z" }
---

# Dependencies

* [`A.java`](/pkg/A.java.md)
```

## Running the Server

### stdio (default)

```bash
java -jar code/context/target/tools.code.context-{version}.jar
```

### streamable HTTP

```bash
java -jar code/context/target/tools.code.context-{version}.jar --transport.mode=streamable-http
```

The MCP endpoint is then served at `http://localhost:8082/mcp` (the port is
configurable through `server.port`).

### stateless HTTP

```bash
java -jar code/context/target/tools.code.context-{version}.jar --transport.mode=stateless-http
```

The MCP endpoint is served at `http://localhost:8082/mcp`, the same as
`streamable-http`, but the server keeps no session between requests: each
JSON-RPC call is handled in isolation. This suits horizontally scaled or
serverless deployments where requests may land on different instances. Clients
connect with the same streamable HTTP configuration shown below.

### HTTPS (TLS 1.3)

To serve the streamable HTTP transport over HTTPS, point the standard Spring
Boot SSL properties at a key store and enable SSL:

```properties
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
```

`TlsConfiguration` then pins the embedded connector to **TLS 1.3** — it forces
`server.ssl.enabled-protocols` to `TLSv1.3`, so older protocols can never be
negotiated even if they are requested. The endpoint is then served at
`https://localhost:8082/mcp`.

It also **prefers the post-quantum `X25519MLKEM768` hybrid key exchange** for the
TLS 1.3 handshake: it sets `jdk.tls.namedGroups` to
`X25519MLKEM768,x25519,secp256r1,secp384r1`, listing the hybrid group first so it
is chosen when both peers support it, with the classical groups kept as a
fallback. `X25519MLKEM768` pairs classical X25519 with NIST ML-KEM-768, so the
session key resists a future quantum attacker while remaining secure if either
primitive is broken. The SunJSSE provider in JDK 25 does not yet ship this group,
so the handshake falls back to X25519 today; because the fallback groups are
listed too, the exchange upgrades to the hybrid automatically once the TLS
provider supports it, with no code change. See
[ADR 0011](../../../../../../../../../../docs/adr/0011-hybrid-post-quantum-key-exchange.md).

## Security

The tools read source files from disk and never write to it, so access is
constrained by design:

- **Allowed roots.** Every `path` argument is resolved to its real location
  (symlinks followed, `..` collapsed) and must fall within a configured allowed
  root, otherwise the call is rejected. Configure the roots with
  `context.allowed-roots` (a `File.pathSeparator`-separated list of absolute
  paths). When left blank, the server's working directory is the single allowed
  root. This prevents a client from steering the scanners at arbitrary files
  such as `/etc` or a user's home directory.

  ```properties
  context.allowed-roots=/home/me/projects:/srv/code
  ```

- **Loopback binding.** The HTTP transports (streamable HTTP and stateless HTTP)
  bind to `127.0.0.1` by default (`server.address`). The `/mcp` endpoint has
  **no authentication**, so it must not be exposed on a routable interface.
  Change `server.address` only after putting authentication in front of it.

- **Bounded depth.** The `depth` argument runs from `1` to `10`: the cap bounds
  the cost of transitive dependency resolution, and the floor refuses a depth of
  zero, which resolves nothing.

## Configuring MCP Clients

### Claude Desktop (stdio)

```json
{
  "mcpServers": {
    "context-engineering": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/tools/code/context/target/tools.code.context-{version}.jar"
      ]
    }
  }
}
```

### Streamable HTTP client

```json
{
  "mcpServers": {
    "context-engineering": {
      "type": "http",
      "url": "http://localhost:8082/mcp"
    }
  }
}
```

## Server Capabilities

The server advertises:

- **Tools**: true (`project_tree`, `find_context`, `estimate_tokens`, `okf_bundle`)
- **Resources**: false
- **Prompts**: false

## Related Documentation

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [MCP Java SDK Documentation](https://github.com/modelcontextprotocol/java-sdk)
- [Data module MCP server](../../../../../../../../../../data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/MCP_USAGE.md)

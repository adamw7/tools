# MCP Server for Context Engineering

This package contains a Model Context Protocol (MCP) server that exposes the
context-engineering capabilities of the `code/context` module — the project-tree
scanner and the class-usage context finder — to MCP clients such as Claude
Desktop, Cline, or any other MCP-compatible client.

## Overview

The server exposes two tools:

- **`project_tree`** — scans a Java or Kotlin project into a tree of folders,
  files and the classes each file depends on, then serialises it as JSON
  (default), Markdown or plain text.
- **`find_context`** — resolves the classes a single source file depends on,
  bounded by a configurable depth, and returns them as a JSON array.

## Architecture

The implementation lives in a package separate from the core finders:

1. **Main.java** — Spring Boot entry point that selects the transport.
2. **McpConfiguration.java** — Spring configuration that wires the transports and
   registers the tools.
3. **ContextTool.java** — the abstraction every tool implements.
4. **ProjectTreeTool.java** / **ContextFinderTool.java** — the two tools.
5. **ToolArguments.java** — shared parsing of the call arguments.

The server uses:

- **Transport**: stdio (default) or streamable HTTP
  (`--transport.mode=streamable-http`), which serves the MCP endpoint at `/mcp`.
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
- `language` (string, optional): `java` (default) or `kotlin`
- `depth` (integer, optional): levels of transitive dependencies to resolve (default `1`)
- `format` (string, optional): `json` (default), `markdown` or `text`

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
- `language` (string, optional): `java` (default) or `kotlin`
- `depth` (integer, optional): levels of transitive dependencies to resolve (default `1`)

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

## Running the Server

### stdio (default)

```bash
java -jar code/context/target/tools.code.context-2.2.0-SNAPSHOT.jar
```

### streamable HTTP

```bash
java -jar code/context/target/tools.code.context-2.2.0-SNAPSHOT.jar --transport.mode=streamable-http
```

The MCP endpoint is then served at `http://localhost:8082/mcp` (the port is
configurable through `server.port`).

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

## Configuring MCP Clients

### Claude Desktop (stdio)

```json
{
  "mcpServers": {
    "context-engineering": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/tools/code/context/target/tools.code.context-2.2.0-SNAPSHOT.jar"
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

- **Tools**: true (`project_tree`, `find_context`)
- **Resources**: false
- **Prompts**: false

## Related Documentation

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [MCP Java SDK Documentation](https://github.com/modelcontextprotocol/java-sdk)
- [Data module MCP server](../../../../../../../../../../data/src/main/java/io/github/adamw7/tools/data/uniqueness/mcp/MCP_USAGE.md)

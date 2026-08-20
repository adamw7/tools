# MCP Server for Uniqueness Checking

This directory contains a Model Context Protocol (MCP) server implementation that provides data uniqueness checking capabilities to MCP clients such as Claude Desktop, Cline, or any other MCP-compatible client.

## Overview

The MCP server exposes a `uniqueness_check` tool that allows AI assistants to analyze CSV files and determine if a specific column contains unique values. This is useful for data validation, identifying potential primary keys, and ensuring data quality.

## Architecture

The implementation consists of three main components:

1. **Main.java** - Spring Boot application entry point that selects the transport (stdio by default)
2. **McpConfiguration.java** - Spring configuration that sets up the MCP server and registers tools
3. **UniquenessTool.java** - Implements the uniqueness checking tool

The server uses:
- **Transport**: stdio (default), streamable-http (`--transport.mode=streamable-http`, served at `/mcp`), or stateless-http (`--transport.mode=stateless-http`, session-less, also served at `/mcp`). Any other value is refused at startup, naming the three
- **MCP SDK**: io.modelcontextprotocol.sdk v2.0.0
- **Framework**: Spring Boot
- **Protocol**: Model Context Protocol (MCP)

## Building the Server

From the root of the repository:

```bash
mvn clean install
```

This creates an executable JAR in `data/target/tools.data-{version}-boot.jar`. The
`boot` classifier keeps the executable server separate from `tools.data-{version}.jar`,
which stays an ordinary library jar for modules that depend on this one.

## Tool Specification

### uniqueness_check

Checks if a given column in a CSV file contains only unique values.

**Parameters:**
- `file` (string, required): Path to the CSV file
- `columns_row` (integer, required): Row number (0-based) that contains column headers
- `columns_name` (string, required): Name of the column to check for uniqueness

**Returns:**
- `true` if all values in the column are unique
- `false` if duplicates are found

**Example:**
```json
{
  "file": "/path/to/data.csv",
  "columns_row": 0,
  "columns_name": "user_id"
}
```

## Configuring MCP Clients

### Claude Desktop

Add the following to your Claude Desktop configuration file:

**macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
**Windows**: `%APPDATA%/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "uniqueness-checker": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/tools/data/target/tools.data-{version}-boot.jar"
      ]
    }
  }
}
```

Replace `/absolute/path/to/tools` with the actual path to your repository.

### Cline (VS Code Extension)

In VS Code settings (settings.json):

```json
{
  "mcp.servers": {
    "uniqueness-checker": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/tools/data/target/tools.data-{version}-boot.jar"
      ]
    }
  }
}
```

### Generic MCP Client Configuration

For any MCP client that supports stdio transport:

```json
{
  "command": "java",
  "args": ["-jar", "/path/to/tools.data-{version}-boot.jar"],
  "transport": "stdio"
}
```

## Usage Examples

Once configured, you can use the tool in conversations with your MCP client:

### Example 1: Basic Uniqueness Check

**User:** I have a CSV file at /data/users.csv with headers in the first row. Can you check if the user_id column has unique values?

**Assistant:** The assistant will call the uniqueness_check tool with:
```json
{
  "file": "/data/users.csv",
  "columns_row": 0,
  "columns_name": "user_id"
}
```

**Response:** true (if user_id values are unique) or false (if duplicates exist)

### Example 2: Data Validation

**User:** I'm working with a product catalog at /data/products.csv. The column names are on row 0. Please verify that the SKU column contains only unique values.

**Assistant:** The assistant will use the tool to check if SKU is a valid unique identifier for the products.

### Example 3: Identifying Primary Key Candidates

**User:** For the file /data/customers.csv with headers on the first row, check if email can serve as a primary key.

**Assistant:** The assistant will verify uniqueness of the email column to determine if it's suitable as a primary key.

## CSV File Format Requirements

The MCP server expects CSV files in the following format:

1. **Column Headers**: Must be present at the specified `columns_row`
2. **Delimiter**: Standard comma (,) delimiter
3. **Encoding**: UTF-8 encoding recommended
4. **File Access**: The server must have read access to the file path

Example CSV structure:
```csv
user_id,username,email,created_date
1,john_doe,john@example.com,2024-01-15
2,jane_smith,jane@example.com,2024-01-16
3,bob_jones,bob@example.com,2024-01-17
```

## Troubleshooting

### Server Not Starting

If the MCP server fails to start:

1. Verify Java is installed: `java -version`
2. Check the JAR file exists at the specified path
3. Ensure you have execute permissions on the JAR
4. Review MCP client logs for error messages

### File Not Found Errors

If you receive file not found errors:

1. Use absolute paths instead of relative paths
2. Verify file permissions
3. Check that the file exists at the specified location
4. Ensure the server process has read access to the file

### Tool Not Available

If the uniqueness_check tool doesn't appear in your MCP client:

1. Restart the MCP client after configuration changes
2. Verify the JSON configuration syntax is correct
3. Check MCP client logs for connection errors
4. Ensure the server process started successfully

## Technical Details

### Communication Protocol

The server communicates using the Model Context Protocol over stdio:
- **Input**: JSON-RPC messages via standard input
- **Output**: JSON-RPC responses via standard output
- **Logging**: Log messages go to a rolling file, `logs/app.log`
  (`src/main/resources/log4j2.properties`). The configuration is deliberately
  file-only: this server owns standard output for the JSON-RPC stream, and a
  console appender there would corrupt the protocol

### Server Capabilities

The server advertises the following capabilities:
- **Tools**: true (provides the uniqueness_check tool)
- **Resources**: false (no resource support)
- **Prompts**: false (no prompt support)

### Implementation Details

The server is implemented using:
- Spring Boot for dependency injection and lifecycle management
- MCP Java SDK for protocol handling
- InMemoryCSVDataSource for loading CSV data
- InMemoryUniquenessCheck for uniqueness validation

## Development

### Running in Development Mode

To run the server directly during development:

```bash
cd data
mvn spring-boot:run
```

### Testing

To test the MCP tool functionality:

```bash
mvn test
```

### Debugging

To enable debug logging, modify the Log4j2 configuration or point Log4j2 at
another one. Keep the replacement file-based for the stdio transport — a console
appender would write into the JSON-RPC stream:

```bash
java -Dlog4j2.configurationFile=log4j2-debug.xml -jar tools.data-{version}-boot.jar
```

## Extending the Server

Tools are written against the `mcp-common` module's own SPI, never against the
MCP SDK: a tool implements `McpTool` and speaks in the transport-neutral
`ToolDefinition`, `ToolArguments` and `ToolResult` types, and
`AbstractMcpConfiguration` translates them for the SDK when it wires the server.
An ArchUnit rule pins this — every concrete `*Tool` must implement `McpTool`.

1. Create a new tool class implementing `McpTool`, exposing its
   `ToolDefinition` (name, description, JSON input schema) and mapping the
   call's arguments to a `ToolResult`
2. Read arguments through `ToolArguments`, which reports a missing or
   ill-typed one for you
3. Add it to the list `McpConfiguration.tools()` returns

Example:
```java
public class MyNewTool implements McpTool {

    private final ToolDefinition toolDefinition = new ToolDefinition("my_tool",
            "Description of what this tool does",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "file", Map.of("type", "string", "description", "filename")
                ),
                "required", List.of("file")
            ));

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public ToolResult apply(Map<String, Object> arguments) {
        String file = ToolArguments.requiredString(arguments, "file");
        return ToolResult.success(resultFor(file));
    }
}
```

Then register it in `McpConfiguration`:
```java
@Override
protected List<McpTool> tools() {
    AllowedPaths allowedPaths = confinement();
    return List.of(new UniquenessTool(allowedPaths), new MyNewTool(allowedPaths));
}
```

A tool that opens files takes the `AllowedPaths` and hands it to every data source
it builds, so the boundary confines this server's tools and nothing else in the
JVM. `PathValidator.setAllowedBaseDir`, which set one boundary for the whole
process, is deprecated for removal.

A `RuntimeException` thrown out of `apply` already becomes an error result, so
there is no need to catch one just to report it over the protocol.

## Related Documentation

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [MCP Java SDK Documentation](https://github.com/modelcontextprotocol/java-sdk)
- [Main Project README](../../../../../../../../../../../README.md)
- [Data Module Documentation](../../../../../../../../../../../README.md#data)
- [Context module MCP server](../../../../../../../../../../../code/context/src/main/java/io/github/adamw7/context/mcp/MCP_USAGE.md)

## License

This project is licensed under the same license as the parent tools repository.

# MCP Server for Uniqueness Checking

This directory contains a Model Context Protocol (MCP) server implementation that provides data uniqueness checking capabilities to MCP clients such as Claude Desktop, Cline, or any other MCP-compatible client.

## Overview

The MCP server exposes a `uniqueness_check` tool that allows AI assistants to analyze CSV files and determine if a specific column contains unique values. This is useful for data validation, identifying potential primary keys, and ensuring data quality.

## Architecture

The implementation consists of three main components:

1. **Main.java** - Spring Boot application entry point that configures stdio transport
2. **McpConfiguration.java** - Spring configuration that sets up the MCP server and registers tools
3. **UniquenessTool.java** - Implements the uniqueness checking tool

The server uses:
- **Transport**: stdio (standard input/output)
- **MCP SDK**: io.modelcontextprotocol.sdk v0.12.1
- **Framework**: Spring Boot
- **Protocol**: Model Context Protocol (MCP)

## Building the Server

From the root of the repository:

```bash
mvn clean install
```

This creates an executable JAR in `data/target/tools.data-{version}.jar`.

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
        "/absolute/path/to/tools/data/target/tools.data-1.4.0-SNAPSHOT.jar"
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
        "/absolute/path/to/tools/data/target/tools.data-1.4.0-SNAPSHOT.jar"
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
  "args": ["-jar", "/path/to/tools.data-1.4.0-SNAPSHOT.jar"],
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
- **Logging**: Log messages are written to stderr (configured via Log4j2)

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

To enable debug logging, modify the Log4j2 configuration or set system properties:

```bash
java -Dlog4j.configurationFile=log4j2-debug.xml -jar tools.data-1.4.0-SNAPSHOT.jar
```

## Extending the Server

To add new tools to the MCP server:

1. Create a new tool class implementing `Function<Map<String, Object>, CallToolResult>`
2. Define the tool specification using `Tool.builder()`
3. Register the tool in `McpConfiguration.java` using `syncServer.addTool()`

Example:
```java
@Component
public class MyNewTool implements Function<Map<String, Object>, CallToolResult> {

    private final Tool toolDefinition = Tool.builder()
        .name("my_tool")
        .description("Description of what this tool does")
        .inputSchema("{ /* JSON schema */ }")
        .build();

    @Override
    public CallToolResult apply(Map<String, Object> arguments) {
        // Tool implementation
        return new CallToolResult(List.of(new TextContent(result)), false);
    }

    public Tool getToolDefinition() {
        return toolDefinition;
    }
}
```

Then register it in `McpConfiguration`:
```java
MyNewTool myTool = new MyNewTool();
SyncToolSpecification.Builder toolSpec = SyncToolSpecification.builder()
    .tool(myTool.getToolDefinition())
    .callHandler((exchange, request) -> myTool.apply(request.arguments()));
syncServer.addTool(toolSpec.build());
```

## Related Documentation

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [MCP Java SDK Documentation](https://github.com/modelcontextprotocol/java-sdk)
- [Main Project README](../../../../../../../../../README.md)
- [Data Module Documentation](../../../../../../../../../README.md#data)

## License

This project is licensed under the same license as the parent tools repository.

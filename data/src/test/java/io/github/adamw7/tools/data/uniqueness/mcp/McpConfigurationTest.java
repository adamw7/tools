package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

public class McpConfigurationTest {

    @Test
    public void happyPath() {
        McpConfiguration config = new McpConfiguration();
        assertFalse(config.objectMapper() == null);
        McpSyncServer server = config.mcpSyncServer(new StdioServerTransportProvider(McpJsonMapper.getDefault()));
        assertFalse(server.getServerCapabilities().tools() == null);
        server.close();
    }
}

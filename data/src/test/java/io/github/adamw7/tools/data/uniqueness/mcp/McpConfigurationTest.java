package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;

public class McpConfigurationTest {

    @Test
    public void happyPath() {
        McpConfiguration config = new McpConfiguration();
        assertFalse(config.objectMapper() == null);
        McpSyncServer server = config.mcpSyncServer(new StdioServerTransportProvider(new JacksonMcpJsonMapper(new ObjectMapper())));
        assertFalse(server.getServerCapabilities().tools() == null);
        server.close();
    }
}

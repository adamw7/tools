package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

public class McpConfigurationTest {

    @Test
    public void happyPath() {
        McpConfiguration config = new McpConfiguration();
        assertFalse(config.objectMapper() == null);
        McpSyncServer server = config.mcpSyncServer(new StdioServerTransportProvider(new JacksonMcpJsonMapper(new ObjectMapper())));
        assertFalse(server.getServerCapabilities().tools() == null);
        server.close();
    }

    @Test
    public void stdioTransportIsNotNull() {
        McpConfiguration config = new McpConfiguration();
        assertFalse(config.stdioServerTransport() == null);
    }

    @Test
    public void streamableTransportIsNotNull() {
        McpConfiguration config = new McpConfiguration();
        assertNotNull(config.streamableServerTransport());
    }

    @Test
    public void streamableServletRegistrationIsNotNull() {
        McpConfiguration config = new McpConfiguration();
        HttpServletStreamableServerTransportProvider transport = config.streamableServerTransport();
        assertNotNull(config.streamableServletRegistration(transport));
    }

    @Test
    public void mcpSyncServerStreamableHasTools() {
        McpConfiguration config = new McpConfiguration();
        HttpServletStreamableServerTransportProvider transport = config.streamableServerTransport();
        McpSyncServer server = config.mcpSyncServerStreamable(transport);
        assertNotNull(server.getServerCapabilities().tools());
        server.close();
    }
}

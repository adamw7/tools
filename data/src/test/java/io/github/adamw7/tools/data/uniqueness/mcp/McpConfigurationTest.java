package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.adamw7.tools.data.source.file.PathValidator;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

public class McpConfigurationTest {

    @AfterEach
    public void clearBaseDir() {
        // tools() confines file access through global PathValidator state; reset it
        // so the restriction does not leak into other tests.
        PathValidator.clearAllowedBaseDir();
    }

    @Test
    public void confinesFileAccessToConfiguredBaseDir(@TempDir Path baseDir) throws IOException {
        Path insideFile = Files.createFile(baseDir.resolve("data.csv"));
        McpConfiguration config = new McpConfiguration();
        config.allowedBaseDir = baseDir.toString();

        config.tools();

        assertThrows(SecurityException.class,
                () -> PathValidator.validate(outsideBase(baseDir)));
        assertDoesNotThrow(() -> PathValidator.validate(insideFile.toRealPath().toString()));
    }

    @Test
    public void defaultsToWorkingDirectoryWhenBaseDirBlank(@TempDir Path outsideDir) throws IOException {
        Path outsideFile = Files.createFile(outsideDir.resolve("data.csv"));
        McpConfiguration config = new McpConfiguration();
        config.allowedBaseDir = "   ";

        config.tools();

        // The JUnit temp dir lives outside the module's working directory, so a file
        // there must be rejected once access is confined to the default base.
        assertThrows(SecurityException.class,
                () -> PathValidator.validate(outsideFile.toRealPath().toString()));
    }

    private String outsideBase(Path baseDir) {
        return baseDir.getParent().resolve("outside-the-base-dir.csv").toString();
    }

    @Test
    public void happyPath() throws Exception {
        McpConfiguration config = new McpConfiguration();
        assertFalse(config.objectMapper() == null);
        // Drive the stdio server from a controllable pipe instead of System.in. The
        // reader thread is non-daemon and cannot be interrupted while blocked on a
        // read, so closing the pipe is the only way to let it terminate and avoid
        // leaking it into the forked test JVM.
        PipedInputStream input = new PipedInputStream();
        PipedOutputStream inputWriter = new PipedOutputStream(input);
        McpSyncServer server = config.mcpSyncServer(new StdioServerTransportProvider(
                new JacksonMcpJsonMapper(new ObjectMapper()), input, OutputStream.nullOutputStream()));
        assertFalse(server.getServerCapabilities().tools() == null);
        server.close();
        inputWriter.close();
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

    @Test
    public void sseTransportIsNotNull() {
        McpConfiguration config = new McpConfiguration();
        assertNotNull(config.sseServerTransport());
    }

    @Test
    public void sseServletRegistrationIsNotNull() {
        McpConfiguration config = new McpConfiguration();
        HttpServletSseServerTransportProvider transport = config.sseServerTransport();
        assertNotNull(config.sseServletRegistration(transport));
    }

    @Test
    public void sseServletRegistrationServesBothEndpoints() {
        McpConfiguration config = new McpConfiguration();
        HttpServletSseServerTransportProvider transport = config.sseServerTransport();
        assertTrue(config.sseServletRegistration(transport).getUrlMappings()
                .containsAll(java.util.List.of("/sse", "/mcp/message")));
    }

    @Test
    public void mcpSyncServerWiresSseTransport() {
        McpConfiguration config = new McpConfiguration();
        McpSyncServer server = config.mcpSyncServer(config.sseServerTransport());
        assertNotNull(server.getServerCapabilities().tools());
        server.close();
    }
}

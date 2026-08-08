package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

public class McpConfigurationTest {

	private final McpConfiguration config = new McpConfiguration();

	@Test
	public void happyPath() throws Exception {
		assertNotNull(config.objectMapper());
		// Drive the stdio server from a controllable pipe instead of System.in. The
		// reader thread is non-daemon and cannot be interrupted while blocked on a
		// read, so closing the pipe is the only way to let it terminate and avoid
		// leaking it into the forked test JVM.
		PipedInputStream input = new PipedInputStream();
		PipedOutputStream inputWriter = new PipedOutputStream(input);
		McpSyncServer server = config.mcpSyncServer(new StdioServerTransportProvider(
				new JacksonMcpJsonMapper(new ObjectMapper()), input, OutputStream.nullOutputStream()));
		assertNotNull(server.getServerCapabilities().tools());
		server.close();
		inputWriter.close();
	}

	@Test
	public void stdioTransportIsNotNull() {
		assertNotNull(config.stdioServerTransport());
	}

	@Test
	public void streamableTransportIsNotNull() {
		assertNotNull(config.streamableServerTransport());
	}

	@Test
	public void streamableServletRegistrationIsNotNull() {
		HttpServletStreamableServerTransportProvider transport = config.streamableServerTransport();
		assertNotNull(config.streamableServletRegistration(transport));
	}

	@Test
	public void mcpSyncServerStreamableHasTools() {
		HttpServletStreamableServerTransportProvider transport = config.streamableServerTransport();
		McpSyncServer server = config.mcpSyncServerStreamable(transport);
		assertNotNull(server.getServerCapabilities().tools());
		server.close();
	}
}

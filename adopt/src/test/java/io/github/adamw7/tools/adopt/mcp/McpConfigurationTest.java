package io.github.adamw7.tools.adopt.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

class McpConfigurationTest {

	private final McpConfiguration config = new McpConfiguration();

	@Test
	void exposesTheAdoptTool() {
		assertEquals(1, config.tools().size());
		assertEquals("adopt_repo", config.tools().get(0).getToolDefinition().name());
	}

	@Test
	void wiresAServerWithToolCapabilities() throws Exception {
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
	void supportsTheSharedTransports() {
		assertNotNull(config.stdioServerTransport());
		assertNotNull(config.streamableServerTransport());
		assertNotNull(config.sseServerTransport());
	}
}

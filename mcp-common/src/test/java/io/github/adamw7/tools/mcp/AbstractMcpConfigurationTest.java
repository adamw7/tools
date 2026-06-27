package io.github.adamw7.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public class AbstractMcpConfigurationTest {

	private static final String SERVER_NAME = "test-server";

	/**
	 * Minimal concrete server that exposes a single no-op tool. It supplies only the
	 * abstract hooks so the inherited transport beans and server build can be driven
	 * directly, the same way the real {@code McpConfiguration} subclasses are.
	 */
	private static final class TestMcpConfiguration extends AbstractMcpConfiguration {

		@Override
		protected String serverName() {
			return SERVER_NAME;
		}

		@Override
		protected List<McpTool> tools() {
			return List.of(new TestTool());
		}
	}

	private static final class TestTool implements McpTool {

		private final Tool toolDefinition = Tool.builder("test_tool",
				Map.of("type", "object", "properties", Map.of())).description("A test tool").build();

		@Override
		public Tool getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public CallToolResult apply(Map<String, Object> arguments) {
			return CallToolResult.builder()
					.content(List.of(TextContent.builder("ok").build())).isError(false).build();
		}
	}

	@Test
	public void objectMapperIsNotNull() {
		assertNotNull(new TestMcpConfiguration().objectMapper());
	}

	@Test
	public void stdioTransportIsNotNull() {
		assertNotNull(new TestMcpConfiguration().stdioServerTransport());
	}

	@Test
	public void streamableTransportIsNotNull() {
		assertNotNull(new TestMcpConfiguration().streamableServerTransport());
	}

	@Test
	public void streamableServletRegistrationIsNotNull() {
		TestMcpConfiguration config = new TestMcpConfiguration();
		HttpServletStreamableServerTransportProvider transport = config.streamableServerTransport();
		assertNotNull(config.streamableServletRegistration(transport));
	}

	@Test
	public void sseTransportIsNotNull() {
		assertNotNull(new TestMcpConfiguration().sseServerTransport());
	}

	@Test
	public void sseServletRegistrationServesBothEndpoints() {
		TestMcpConfiguration config = new TestMcpConfiguration();
		HttpServletSseServerTransportProvider transport = config.sseServerTransport();
		assertTrue(config.sseServletRegistration(transport).getUrlMappings()
				.containsAll(List.of("/sse", "/mcp/message")));
	}

	@Test
	public void mcpSyncServerRegistersToolsOverStdio() throws Exception {
		TestMcpConfiguration config = new TestMcpConfiguration();
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
	public void mcpSyncServerWiresSseTransport() {
		TestMcpConfiguration config = new TestMcpConfiguration();
		McpSyncServer server = config.mcpSyncServer(config.sseServerTransport());
		assertNotNull(server.getServerCapabilities().tools());
		server.close();
	}

	@Test
	public void mcpSyncServerStreamableHasTools() {
		TestMcpConfiguration config = new TestMcpConfiguration();
		HttpServletStreamableServerTransportProvider transport = config.streamableServerTransport();
		McpSyncServer server = config.mcpSyncServerStreamable(transport);
		assertNotNull(server.getServerCapabilities().tools());
		server.close();
	}
}

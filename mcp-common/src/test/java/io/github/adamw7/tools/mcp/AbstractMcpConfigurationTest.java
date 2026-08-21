package io.github.adamw7.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;

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

		private final ToolDefinition toolDefinition = new ToolDefinition("test_tool", "A test tool",
				Map.of("type", "object", "properties", Map.of()));

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public ToolResult apply(Map<String, Object> arguments) {
			return ToolResult.success("ok");
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
	public void statelessTransportIsNotNull() {
		assertNotNull(new TestMcpConfiguration().statelessServerTransport());
	}

	@Test
	public void statelessServletRegistrationServesTheMcpEndpoint() {
		TestMcpConfiguration config = new TestMcpConfiguration();
		HttpServletStatelessServerTransport transport = config.statelessServerTransport();
		assertTrue(config.statelessServletRegistration(transport).getUrlMappings().contains("/mcp"));
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

	/**
	 * The handshake must name the build the server was packaged from, so a client can
	 * tell which release it is talking to.
	 */
	@Test
	public void mcpSyncServerStreamableAdvertisesTheBuildVersion() {
		TestMcpConfiguration config = new TestMcpConfiguration();
		McpSyncServer server = config.mcpSyncServerStreamable(config.streamableServerTransport());
		assertEquals(ServerVersion.current(), server.getServerInfo().version());
		assertEquals(SERVER_NAME, server.getServerInfo().name());
		server.close();
	}

	@Test
	public void mcpStatelessSyncServerAdvertisesTheBuildVersion() {
		TestMcpConfiguration config = new TestMcpConfiguration();
		McpStatelessSyncServer server = config.mcpStatelessSyncServer(config.statelessServerTransport());
		assertEquals(ServerVersion.current(), server.getServerInfo().version());
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

	@Test
	public void mcpStatelessSyncServerRegistersTools() {
		TestMcpConfiguration config = new TestMcpConfiguration();
		HttpServletStatelessServerTransport transport = config.statelessServerTransport();
		McpStatelessSyncServer server = config.mcpStatelessSyncServer(transport);
		assertTrue(server.listTools().stream().anyMatch(tool -> "test_tool".equals(tool.name())));
		server.close();
	}

	/**
	 * {@link AbstractMcpConfiguration#mcpSyncServer(McpServerTransportProvider)} is
	 * conditional on an {@link McpServerTransportProvider} bean, so it can only match
	 * where the transport implements that interface. The streamable provider does not
	 * — it extends {@code McpServerTransportProviderBase} directly — which is why
	 * exactly one {@link McpSyncServer} is defined in every mode and no server sets
	 * {@code spring.main.allow-bean-definition-overriding}. Pinned here because the
	 * hierarchy belongs to the MCP SDK: an SDK release that widened it would silently
	 * give {@code streamable-http} two sync servers to choose between.
	 */
	@Test
	public void onlyTheStdioTransportSatisfiesTheSessionBasedServerCondition() {
		TestMcpConfiguration config = new TestMcpConfiguration();

		assertInstanceOf(McpServerTransportProvider.class, config.stdioServerTransport());
		assertFalse(config.streamableServerTransport() instanceof McpServerTransportProvider);
	}

	@Test
	public void safeApplyReturnsTheToolResultWhenItSucceeds() {
		ToolResult result = new TestMcpConfiguration().safeApply(new TestTool(), Map.of());
		assertFalse(result.isError());
		assertEquals("ok", result.text());
	}

	@Test
	public void safeApplyTurnsAThrownExceptionIntoAnErrorResult() {
		ToolResult result = new TestMcpConfiguration().safeApply(new ThrowingTool(), Map.of());
		assertTrue(result.isError());
		assertEquals("boom failed: bad argument", result.text());
	}

	/**
	 * Every tool of every server fails through this one handler, so it is where a
	 * credential-bearing argument reaches the client if anywhere does: a failing clone
	 * quotes the URL it was handed back in its own message.
	 */
	@Test
	public void safeApplyAnswersWithoutTheCredentialsTheCallCarried() {
		ToolResult result = new TestMcpConfiguration().safeApply(new CredentialQuotingTool(),
				Map.of("repository_url", "https://x-access-token:s3cr3t@github.com/owner/repo.git"));

		assertTrue(result.isError());
		assertFalse(result.text().contains("s3cr3t"), result.text());
		assertEquals("clone failed: fatal: could not read Username for 'https://***@github.com/owner/repo.git'",
				result.text());
	}

	private static final class CredentialQuotingTool implements McpTool {

		private final ToolDefinition toolDefinition = new ToolDefinition("clone", "Quotes what it was given",
				Map.of("type", "object", "properties", Map.of()));

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public ToolResult apply(Map<String, Object> arguments) {
			throw new IllegalStateException("fatal: could not read Username for "
					+ "'https://x-access-token:s3cr3t@github.com/owner/repo.git'");
		}
	}

	private static final class ThrowingTool implements McpTool {

		private final ToolDefinition toolDefinition = new ToolDefinition("boom", "Always fails",
				Map.of("type", "object", "properties", Map.of()));

		@Override
		public ToolDefinition getToolDefinition() {
			return toolDefinition;
		}

		@Override
		public ToolResult apply(Map<String, Object> arguments) {
			throw new IllegalArgumentException("bad argument");
		}
	}
}

package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

public class McpConfigurationTest {

	private final McpConfiguration config = new McpConfiguration();

	@AfterEach
	public void clearBaseDir() {
		// tools() confines file access through global PathValidator state; reset it
		// so the restriction does not leak into other tests.
		PathValidator.clearAllowedBaseDir();
	}

	@Test
	public void confinesFileAccessToConfiguredBaseDir(@TempDir Path baseDir) throws IOException {
		Path insideFile = Files.createFile(baseDir.resolve("data.csv"));
		config.allowedBaseDir = baseDir.toString();

		config.tools();

		assertThrows(SecurityException.class, () -> PathValidator.validate(outsideBase(baseDir)));
		assertDoesNotThrow(() -> PathValidator.validate(insideFile.toRealPath().toString()));
	}

	@Test
	public void defaultsToWorkingDirectoryWhenBaseDirBlank(@TempDir Path outsideDir) throws IOException {
		Path outsideFile = Files.createFile(outsideDir.resolve("data.csv"));
		config.allowedBaseDir = "   ";

		config.tools();

		// The JUnit temp dir lives outside the module's working directory, so a file
		// there must be rejected once access is confined to the default base.
		assertThrows(SecurityException.class, () -> PathValidator.validate(outsideFile.toRealPath().toString()));
	}

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

	private String outsideBase(Path baseDir) {
		return baseDir.getParent().resolve("outside-the-base-dir.csv").toString();
	}
}

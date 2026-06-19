package io.github.adamw7.tools.enforcer.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class McpServersValidRuleTest {

	private static final String VALID_MCP = """
			{
			  "mcpServers": {
			    "filesystem": {
			      "command": "npx",
			      "args": [ "-y", "@modelcontextprotocol/server-filesystem" ]
			    },
			    "remote": {
			      "type": "sse",
			      "url": "https://example.com/sse"
			    }
			  }
			}
			""";

	@TempDir
	private Path tempDir;

	@Test
	void passesForValidConfiguration() {
		assertDoesNotThrow(ruleFor(VALID_MCP)::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new McpServersValidRule()::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void passesWhenFileIsAbsentBecauseMcpJsonIsOptional() {
		McpServersValidRule rule = new McpServersValidRule();
		rule.setMcpFile(tempDir.resolve("absent.json").toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenFileIsEmpty() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor("   ")::execute);
		assertTrue(exception.getMessage().contains("empty"), exception.getMessage());
	}

	@Test
	void failsWhenJsonIsMalformed() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": ")::execute);
		assertTrue(exception.getMessage().contains("not valid JSON"), exception.getMessage());
	}

	@Test
	void failsWhenMcpServersObjectIsMissing() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor("{ }")::execute);
		assertTrue(exception.getMessage().contains("missing the 'mcpServers' object"), exception.getMessage());
	}

	@Test
	void failsWhenServerIsNotAnObject() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"broken\": \"npx\" } }")::execute);
		assertTrue(exception.getMessage().contains("server 'broken' must be a JSON object"), exception.getMessage());
	}

	@Test
	void failsWhenStdioServerHasNoCommand() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"local\": { \"type\": \"stdio\" } } }")::execute);
		assertTrue(exception.getMessage().contains("server 'local' (stdio) is missing a 'command'"),
				exception.getMessage());
	}

	@Test
	void failsWhenRemoteServerHasNoUrl() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"remote\": { \"type\": \"http\" } } }")::execute);
		assertTrue(exception.getMessage().contains("server 'remote' (http) is missing a 'url'"),
				exception.getMessage());
	}

	@Test
	void failsWhenTransportCannotBeInferred() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"mystery\": { } } }")::execute);
		assertTrue(exception.getMessage().contains("must declare a 'command' (stdio) or a 'type' with a 'url'"),
				exception.getMessage());
	}

	@Test
	void failsWhenTypeIsUnsupported() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"odd\": { \"type\": \"htttp\", \"url\": \"https://x\" } } }")::execute);
		assertTrue(exception.getMessage().contains("server 'odd' has an unsupported type: htttp"),
				exception.getMessage());
	}

	@Test
	void failsWhenARequiredServerIsMissing() {
		McpServersValidRule rule = ruleFor(VALID_MCP);
		rule.setRequiredServers(List.of("github"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing required server: github"), exception.getMessage());
	}

	@Test
	void failsWhenAForbiddenServerIsPresent() {
		McpServersValidRule rule = ruleFor(VALID_MCP);
		rule.setForbiddenServers(List.of("remote"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("forbidden server: remote"), exception.getMessage());
	}

	@Test
	void passesWhenServerPolicyIsSatisfied() {
		McpServersValidRule rule = ruleFor(VALID_MCP);
		rule.setRequiredServers(List.of("filesystem"));
		rule.setForbiddenServers(List.of("github"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void warnsInsteadOfFailingWhenSeverityIsWarn() {
		McpServersValidRule rule = ruleFor("{ \"mcpServers\": { \"mystery\": { } } }");
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("mystery")), logger.warnings().toString());
	}

	private McpServersValidRule ruleFor(String content) {
		Path file = tempDir.resolve(".mcp.json");
		writeString(file, content);
		McpServersValidRule rule = new McpServersValidRule();
		rule.setMcpFile(file.toFile());
		return rule;
	}

	private static void writeString(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}
}

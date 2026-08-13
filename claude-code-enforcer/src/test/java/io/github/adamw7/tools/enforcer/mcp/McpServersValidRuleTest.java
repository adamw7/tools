package io.github.adamw7.tools.enforcer.mcp;

import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		assertFailure(EnforcerRuleException.class, new McpServersValidRule()::execute, "not configured");
	}

	@Test
	void passesWhenFileIsAbsentBecauseMcpJsonIsOptional() {
		McpServersValidRule rule = new McpServersValidRule();
		rule.setMcpFile(tempDir.resolve("absent.json").toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenFileIsEmpty() {
		assertFailure(EnforcerRuleException.class, ruleFor("   ")::execute, "empty");
	}

	@Test
	void failsWhenJsonIsMalformed() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"mcpServers\": ")::execute, "not valid JSON");
	}

	@Test
	void failsWhenMcpServersObjectIsMissing() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ }")::execute, "missing the 'mcpServers' object");
	}

	@Test
	void failsWhenServerIsNotAnObject() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"mcpServers\": { \"broken\": \"npx\" } }")::execute,
				"server 'broken' must be a JSON object");
	}

	@Test
	void failsWhenStdioServerHasNoCommand() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"local\": { \"type\": \"stdio\" } } }")::execute,
				"server 'local' (stdio) is missing a 'command'");
	}

	@Test
	void failsWhenRemoteServerHasNoUrl() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"remote\": { \"type\": \"http\" } } }")::execute,
				"server 'remote' (http) is missing a 'url'");
	}

	@Test
	void failsWhenTransportCannotBeInferred() {
		assertFailure(EnforcerRuleException.class, ruleFor("{ \"mcpServers\": { \"mystery\": { } } }")::execute,
				"must declare a 'command' (stdio) or a 'type' with a 'url'");
	}

	@Test
	void failsWhenTypeIsUnsupported() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"odd\": { \"type\": \"htttp\", \"url\": \"https://x\" } } }")::execute,
				"server 'odd' has an unsupported type: htttp");
	}

	@Test
	void failsWhenARequiredServerIsMissing() {
		McpServersValidRule rule = ruleFor(VALID_MCP);
		rule.setRequiredServers(List.of("github"));

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required server: github");
	}

	@Test
	void failsWhenAForbiddenServerIsPresent() {
		McpServersValidRule rule = ruleFor(VALID_MCP);
		rule.setForbiddenServers(List.of("remote"));

		assertFailure(EnforcerRuleException.class, rule::execute, "forbidden server: remote");
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

}

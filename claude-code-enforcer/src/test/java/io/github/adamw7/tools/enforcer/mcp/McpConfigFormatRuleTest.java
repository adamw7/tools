package io.github.adamw7.tools.enforcer.mcp;

import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class McpConfigFormatRuleTest {

	private static final String VALID_MCP = """
			{
			  "mcpServers": {
			    "filesystem": {
			      "command": "npx",
			      "args": [ "-y", "@modelcontextprotocol/server-filesystem" ],
			      "env": { "ROOT": "/srv" }
			    },
			    "remote": {
			      "type": "sse",
			      "url": "https://example.com/sse",
			      "headers": { "Authorization": "Bearer x" }
			    }
			  }
			}
			""";

	@TempDir
	private Path tempDir;

	@Test
	void passesForAValidConfiguration() {
		assertDoesNotThrow(ruleFor(VALID_MCP)::execute);
	}

	@Test
	void passesWhenFileIsAbsentBecauseMcpJsonIsOptional() {
		McpConfigFormatRule rule = new McpConfigFormatRule();
		rule.setMcpFile(tempDir.resolve("absent.json").toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		assertFailure(EnforcerRuleException.class, new McpConfigFormatRule()::execute, "not configured");
	}

	@Test
	void failsWhenArgsIsNotAnArray() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"fs\": { \"command\": \"npx\", \"args\": \"-y\" } } }")::execute,
				"'args' that is not an array");
	}

	@Test
	void failsWhenArgsHasANonStringEntry() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"fs\": { \"command\": \"npx\", \"args\": [ 7 ] } } }")::execute,
				"non-string entry in 'args'");
	}

	@Test
	void failsWhenEnvIsNotAnObject() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"fs\": { \"command\": \"npx\", \"env\": [ ] } } }")::execute,
				"'env' that is not an object");
	}

	@Test
	void failsWhenAnEnvValueIsNotAString() {
		String config = "{ \"mcpServers\": { \"fs\": { \"command\": \"npx\", \"env\": { \"PORT\": 8080 } } } }";

		assertFailure(EnforcerRuleException.class, ruleFor(config)::execute, "non-string value for 'env.PORT'");
	}

	@Test
	void failsWhenAHeaderValueIsNotAString() {
		String config = "{ \"mcpServers\": { \"r\": { \"type\": \"http\", \"url\": \"https://x.io\","
				+ " \"headers\": { \"X\": 1 } } } }";

		assertFailure(EnforcerRuleException.class, ruleFor(config)::execute, "non-string value for 'headers.X'");
	}

	@Test
	void failsWhenUrlIsMalformed() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"r\": { \"type\": \"sse\", \"url\": \"not a url\" } } }")::execute,
				"malformed 'url'");
	}

	@Test
	void failsWhenAServerDeclaresBothCommandAndUrl() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"x\": { \"command\": \"npx\", \"url\": \"https://x.io\" } } }")::execute,
				"declares both a 'command' and a 'url'");
	}

	@Test
	void failsWhenHttpsIsRequiredButUrlIsPlainHttp() {
		McpConfigFormatRule rule = ruleFor(
				"{ \"mcpServers\": { \"r\": { \"type\": \"http\", \"url\": \"http://x.io\" } } }");
		rule.setRequireHttps(true);

		assertFailure(EnforcerRuleException.class, rule::execute, "must use an https 'url'");
	}

	@Test
	void passesWhenHttpsIsRequiredAndUrlIsHttps() {
		McpConfigFormatRule rule = ruleFor(
				"{ \"mcpServers\": { \"r\": { \"type\": \"http\", \"url\": \"https://x.io\" } } }");
		rule.setRequireHttps(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void warnsInsteadOfFailingWhenSeverityIsWarn() {
		McpConfigFormatRule rule = ruleFor(
				"{ \"mcpServers\": { \"fs\": { \"command\": \"npx\", \"args\": \"-y\" } } }");
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("args")), logger.warnings().toString());
	}

	@Test
	void failsWhenMcpServersIsNotAnObject() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": [ { \"command\": \"npx\", \"url\": \"nonsense\" } ] }")::execute);

		// An 'mcpServers' of the wrong shape used to look exactly like an absent
		// one, so every server inside it went unvalidated.
		assertTrue(exception.getMessage().contains("'mcpServers' must be a JSON object"), exception.getMessage());
	}

	@Test
	void passesWhenThereAreNoMcpServersAtAll() {
		assertDoesNotThrow(ruleFor("{ \"other\": true }")::execute);
	}

	@Test
	void leavesAServerEntryThatIsNotAnObjectToTheTransportRule() {
		// This rule validates the fields around a transport; whether an entry is
		// an object at all is McpServersValidRule's call, so it is not reported
		// twice.
		assertDoesNotThrow(ruleFor("{ \"mcpServers\": { \"fs\": \"npx\" } }")::execute);
	}

	@Test
	void failsWhenAUrlIsNotAString() {
		// Skipping it silently let a JSON number stand in for a URL nobody can
		// connect to: the entry had a 'url', so nothing reported it as missing, and
		// nothing read it as one either.
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"remote\": { \"type\": \"sse\", \"url\": 8080 } } }")::execute,
				"has a 'url' that is not a string");
	}

	@Test
	void failsWhenAUrlHasNoScheme() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"remote\": { \"url\": \"example.com/sse\" } } }")::execute,
				"malformed 'url'");
	}

	@Test
	void failsWhenAUrlHasNoHost() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"remote\": { \"url\": \"http:///sse\" } } }")::execute,
				"malformed 'url'");
	}

	@Test
	void failsWhenAUrlUsesASchemeThatIsNotHttp() {
		assertFailure(EnforcerRuleException.class,
				ruleFor("{ \"mcpServers\": { \"remote\": { \"url\": \"ftp://example.com/sse\" } } }")::execute,
				"malformed 'url'");
	}

	@Test
	void acceptsAnUppercaseUrlScheme() {
		McpConfigFormatRule rule = ruleFor(
				"{ \"mcpServers\": { \"remote\": { \"url\": \"HTTPS://example.com/sse\" } } }");
		rule.setRequireHttps(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void namesTheMcpFileInItsDescription() {
		McpConfigFormatRule rule = ruleFor(VALID_MCP);

		assertTrue(rule.toString().contains(".mcp.json"), rule.toString());
	}

	private McpConfigFormatRule ruleFor(String content) {
		Path file = tempDir.resolve(".mcp.json");
		writeString(file, content);
		McpConfigFormatRule rule = new McpConfigFormatRule();
		rule.setMcpFile(file.toFile());
		return rule;
	}

	/**
	 * Claude Code expands an environment variable in {@code .mcp.json} before it ever
	 * reaches a URL parser, and {@code noSecrets} tells authors to write one there
	 * rather than a literal credential. Judging the unexpanded text reported a
	 * configuration Claude Code loads happily as malformed.
	 */
	@Test
	void passesWhenTheUrlIsAssembledFromABracedVariable() {
		assertDoesNotThrow(ruleFor(
				"{ \"mcpServers\": { \"r\": { \"type\": \"http\", \"url\": \"https://${MCP_HOST}/mcp\" } } }")::execute);
	}

	@Test
	void passesWhenTheUrlIsAssembledFromAPlainVariable() {
		assertDoesNotThrow(ruleFor(
				"{ \"mcpServers\": { \"r\": { \"type\": \"http\", \"url\": \"$MCP_BASE/mcp\" } } }")::execute);
	}

	/**
	 * {@code java.net.URI} reports no host for a name it considers non-compliant, so
	 * reading the host turned a container or service name — where an underscore is
	 * routine — into a reported malformation.
	 */
	@Test
	void passesWhenTheUrlHostCarriesAnUnderscore() {
		assertDoesNotThrow(ruleFor(
				"{ \"mcpServers\": { \"r\": { \"type\": \"http\", \"url\": \"https://mcp_gateway:8080/mcp\" } } }")::execute);
	}
}

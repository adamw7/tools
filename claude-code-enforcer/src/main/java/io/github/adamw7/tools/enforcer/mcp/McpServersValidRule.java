package io.github.adamw7.tools.enforcer.mcp;

import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;
import io.github.adamw7.tools.enforcer.rule.Violations;

/**
 * Enforcer rule that fails the build when the project's {@code .mcp.json} is
 * empty, not valid JSON, or declares a malformed server. Every entry under
 * {@code mcpServers} must be a JSON object whose transport is well formed: a
 * {@code stdio} server (the default when no {@code type} is declared) must carry a
 * non-blank {@code command}, an {@code sse} or {@code http} server a non-blank
 * {@code url}, and an explicit {@code type} outside {@code allowedTypes} —
 * {@code stdio}, {@code sse}, and {@code http} by default — is reported, which
 * catches a mistyped {@code htttp}.
 * <p>
 * A project-level {@code .mcp.json} is optional in Claude Code, so an absent file
 * is treated as a pass. The rule can also assert policy on the configured servers:
 * {@code requiredServers} must all be present and {@code forbiddenServers} all
 * absent, so a project can mandate an MCP server it relies on or ban one it does
 * not want committed. All problems found are reported together.
 */
@Named("mcpServersValid")
public class McpServersValidRule extends JsonFileRule {

	/** The keys shared with {@link McpConfigFormatRule}, named once in {@link McpServers}. */
	private static final String MCP_SERVERS_KEY = McpServers.SECTION_KEY;
	private static final String COMMAND_KEY = McpServers.COMMAND_KEY;
	private static final String URL_KEY = McpServers.URL_KEY;

	private static final String TYPE_KEY = "type";
	private static final String STDIO_TYPE = "stdio";
	private static final String SSE_TYPE = "sse";
	private static final String HTTP_TYPE = "http";
	private static final List<String> DEFAULT_ALLOWED_TYPES = List.of(STDIO_TYPE, SSE_TYPE, HTTP_TYPE);

	/** The {@code .mcp.json} file to validate. Injected from the rule configuration. */
	private File mcpFile;

	/** Server names that must appear under {@code mcpServers}. */
	private List<String> requiredServers;

	/** Server names that must not appear under {@code mcpServers}. */
	private List<String> forbiddenServers;

	/** Optional override for the allowed transport types. */
	private List<String> allowedTypes;

	/** A project-level {@code .mcp.json} is optional in Claude Code, so an absent file is a pass. */
	public McpServersValidRule() {
		super("mcpFile", "mcp.json", OPTIONAL);
	}

	@Override
	protected File jsonFile() {
		return mcpFile;
	}

	@Override
	protected void collectViolations(JsonNode mcp, List<String> violations) {
		JsonNode servers = JsonNodes.objectAt(mcp, MCP_SERVERS_KEY);
		if (servers == null) {
			violations.add("mcp.json is missing the 'mcpServers' object");
			return;
		}
		McpServers.forEach(servers, (name, server) -> collectServerViolations(name, server, violations));
		Violations.each(requiredServers, name -> !servers.has(name),
				name -> "mcp.json is missing required server: " + name, violations);
		Violations.each(forbiddenServers, servers::has,
				name -> "mcp.json contains forbidden server: " + name, violations);
	}

	/**
	 * A server with no explicit {@code type} is a stdio server, inferred from its
	 * {@code command}; an explicit type must be allowed and then carries either a
	 * {@code command} (stdio) or a {@code url} (sse/http).
	 */
	private void collectServerViolations(String name, JsonNode server, List<String> violations) {
		if (server == null) {
			McpServers.add(name, "must be a JSON object", violations);
			return;
		}
		String type = JsonNodes.textAt(server, TYPE_KEY, "").strip();
		if (type.isBlank()) {
			collectInferredTransportViolations(name, server, violations);
		} else if (!Objects.requireNonNullElse(allowedTypes, DEFAULT_ALLOWED_TYPES).contains(type)) {
			McpServers.add(name, "has an unsupported type: " + type, violations);
		} else if (type.equals(STDIO_TYPE)) {
			collectCommandViolation(name, server, violations);
		} else {
			collectUrlViolation(name, type, server, violations);
		}
	}

	private void collectInferredTransportViolations(String name, JsonNode server, List<String> violations) {
		if (server.has(COMMAND_KEY)) {
			collectCommandViolation(name, server, violations);
		} else {
			McpServers.add(name, "must declare a 'command' (stdio) or a 'type' with a 'url' (sse/http)", violations);
		}
	}

	private void collectCommandViolation(String name, JsonNode server, List<String> violations) {
		if (JsonNodes.textAt(server, COMMAND_KEY, "").isBlank()) {
			McpServers.add(name, "(stdio) is missing a 'command'", violations);
		}
	}

	private void collectUrlViolation(String name, String type, JsonNode server, List<String> violations) {
		if (JsonNodes.textAt(server, URL_KEY, "").isBlank()) {
			McpServers.add(name, "(" + type + ") is missing a 'url'", violations);
		}
	}

	void setMcpFile(File mcpFile) {
		this.mcpFile = mcpFile;
	}

	void setRequiredServers(List<String> requiredServers) {
		this.requiredServers = requiredServers;
	}

	void setForbiddenServers(List<String> forbiddenServers) {
		this.forbiddenServers = forbiddenServers;
	}

	void setAllowedTypes(List<String> allowedTypes) {
		this.allowedTypes = allowedTypes;
	}
}

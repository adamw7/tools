package io.github.adamw7.tools.enforcer.mcp;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import org.json.JSONObject;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;

/**
 * Enforcer rule that fails the build when the project's {@code .mcp.json} is
 * missing, empty, or not valid JSON. Beyond that baseline it validates the shape
 * of every entry under the {@code mcpServers} object: each server must be a JSON
 * object whose transport is well formed. A {@code stdio} server (the default when
 * no {@code type} is declared) must carry a non-blank {@code command}; an
 * {@code sse} or {@code http} server must carry a non-blank {@code url}. An
 * explicit {@code type} outside the allowed set is reported, which catches a
 * mistyped {@code htttp}.
 * <p>
 * A project-level {@code .mcp.json} is optional in Claude Code, so an absent file
 * is treated as a pass; the rule only fails when the file is present and
 * malformed. The rule can also assert policy on the configured servers:
 * {@code requiredServers}
 * must all be present and {@code forbiddenServers} must all be absent, so a project
 * can mandate an MCP server it relies on or ban one it does not want committed. The
 * {@code allowedTypes} whitelist defaults to {@code stdio}, {@code sse}, and
 * {@code http} and can be overridden.
 * <p>
 * All problems found are reported together.
 */
@Named("mcpServersValid")
public class McpServersValidRule extends JsonFileRule {

	private static final String MCP_SERVERS_KEY = "mcpServers";
	private static final String TYPE_KEY = "type";
	private static final String COMMAND_KEY = "command";
	private static final String URL_KEY = "url";
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

	@Override
	protected File jsonFile() {
		return mcpFile;
	}

	@Override
	protected String fileParameter() {
		return "mcpFile";
	}

	@Override
	protected String description() {
		return "mcp.json";
	}

	@Override
	protected String header() {
		return "mcp.json is not well formed:";
	}

	/** A project-level {@code .mcp.json} is optional in Claude Code, so an absent file is a pass. */
	@Override
	protected void handleMissingFile(File file) {
	}

	@Override
	protected void collectViolations(JSONObject mcp, List<String> violations) {
		collectServersViolations(mcp, violations);
	}

	private void collectServersViolations(JSONObject mcp, List<String> violations) {
		JSONObject servers = mcp.optJSONObject(MCP_SERVERS_KEY);
		if (servers == null) {
			violations.add("mcp.json is missing the 'mcpServers' object");
		} else {
			collectKnownServersViolations(servers, violations);
		}
	}

	private void collectKnownServersViolations(JSONObject servers, List<String> violations) {
		for (String name : servers.keySet()) {
			collectServerViolations(name, servers.optJSONObject(name), violations);
		}
		collectRequiredServers(servers, violations);
		collectForbiddenServers(servers, violations);
	}

	private void collectServerViolations(String name, JSONObject server, List<String> violations) {
		if (server == null) {
			violations.add("mcp.json server '" + name + "' must be a JSON object");
		} else {
			collectTransportViolations(name, server, violations);
		}
	}

	private void collectTransportViolations(String name, JSONObject server, List<String> violations) {
		String type = server.optString(TYPE_KEY, "").strip();
		if (type.isBlank()) {
			collectInferredTransportViolations(name, server, violations);
		} else {
			collectExplicitTransportViolations(name, server, type, violations);
		}
	}

	private void collectInferredTransportViolations(String name, JSONObject server, List<String> violations) {
		if (server.has(COMMAND_KEY)) {
			collectCommandViolation(name, server, violations);
		} else {
			violations.add("mcp.json server '" + name
					+ "' must declare a 'command' (stdio) or a 'type' with a 'url' (sse/http)");
		}
	}

	private void collectExplicitTransportViolations(String name, JSONObject server, String type,
			List<String> violations) {
		if (!allowedTypes().contains(type)) {
			violations.add("mcp.json server '" + name + "' has an unsupported type: " + type);
		} else if (type.equals(STDIO_TYPE)) {
			collectCommandViolation(name, server, violations);
		} else {
			collectUrlViolation(name, type, server, violations);
		}
	}

	private void collectCommandViolation(String name, JSONObject server, List<String> violations) {
		if (server.optString(COMMAND_KEY, "").isBlank()) {
			violations.add("mcp.json server '" + name + "' (stdio) is missing a 'command'");
		}
	}

	private void collectUrlViolation(String name, String type, JSONObject server, List<String> violations) {
		if (server.optString(URL_KEY, "").isBlank()) {
			violations.add("mcp.json server '" + name + "' (" + type + ") is missing a 'url'");
		}
	}

	private void collectRequiredServers(JSONObject servers, List<String> violations) {
		if (requiredServers == null) {
			return;
		}
		for (String name : requiredServers) {
			if (!servers.has(name)) {
				violations.add("mcp.json is missing required server: " + name);
			}
		}
	}

	private void collectForbiddenServers(JSONObject servers, List<String> violations) {
		if (forbiddenServers == null) {
			return;
		}
		for (String name : forbiddenServers) {
			if (servers.has(name)) {
				violations.add("mcp.json contains forbidden server: " + name);
			}
		}
	}

	private List<String> allowedTypes() {
		return allowedTypes != null ? allowedTypes : DEFAULT_ALLOWED_TYPES;
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

	@Override
	public String toString() {
		return String.format("McpServersValidRule[mcpFile=%s]", mcpFile);
	}
}

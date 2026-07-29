package io.github.adamw7.tools.enforcer.mcp;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;

/**
 * Enforcer rule that fails the build when an {@code .mcp.json} server entry is
 * structurally well formed at the transport level but wrong in its details. Where
 * {@link McpServersValidRule} checks that a server declares the right
 * {@code command} or {@code url} for its transport, this rule validates the
 * optional fields around them:
 * <ul>
 * <li>{@code args} must be an array of strings;</li>
 * <li>{@code env} and {@code headers} must be objects whose values are all
 * strings;</li>
 * <li>{@code url} must be a syntactically valid {@code http} or {@code https}
 * URL (and {@code https} only when {@code requireHttps} is set); and</li>
 * <li>a server must not declare both a {@code command} and a {@code url}, which
 * mixes a stdio and a remote transport in one entry.</li>
 * </ul>
 * <p>
 * A project-level {@code .mcp.json} is optional, so an absent file is a pass, as is
 * one that declares no {@code mcpServers}; an {@code mcpServers} that is present
 * and is not an object is reported rather than skipped, so a mistyped section
 * cannot pass unvalidated. All problems found are reported together.
 */
@Named("mcpConfigFormat")
public class McpConfigFormatRule extends JsonFileRule {

	private static final String MCP_SERVERS_KEY = "mcpServers";
	private static final String COMMAND_KEY = "command";
	private static final String ARGS_KEY = "args";
	private static final String ENV_KEY = "env";
	private static final String HEADERS_KEY = "headers";
	private static final String URL_KEY = "url";
	private static final String HTTP_SCHEME = "http";
	private static final String HTTPS_SCHEME = "https";

	/** The {@code .mcp.json} file to validate. Injected from the rule configuration. */
	private File mcpFile;

	/** When true, a server {@code url} must use {@code https} rather than plain {@code http}. */
	private boolean requireHttps;

	public McpConfigFormatRule() {
		super("mcpFile", "mcp.json");
	}

	@Override
	protected File jsonFile() {
		return mcpFile;
	}

	@Override
	protected String header() {
		return "mcp.json server configuration is not well formed:";
	}

	/** A project-level {@code .mcp.json} is optional in Claude Code, so an absent file is a pass. */
	@Override
	protected void handleMissingFile(File file) {
	}

	@Override
	protected void collectViolations(JsonNode mcp, List<String> violations) {
		if (!mcp.has(MCP_SERVERS_KEY)) {
			return;
		}
		JsonNode servers = JsonNodes.objectAt(mcp, MCP_SERVERS_KEY);
		if (servers == null) {
			violations.add("mcp.json 'mcpServers' must be a JSON object");
			return;
		}
		for (String name : JsonNodes.fieldNames(servers)) {
			collectServerViolations(name, JsonNodes.objectAt(servers, name), violations);
		}
	}

	private void collectServerViolations(String name, JsonNode server, List<String> violations) {
		if (server == null) {
			return;
		}
		if (server.has(COMMAND_KEY) && server.has(URL_KEY)) {
			add(name, "declares both a 'command' and a 'url'", violations);
		}
		collectArgsViolations(name, server, violations);
		collectStringMapViolations(name, server, ENV_KEY, violations);
		collectStringMapViolations(name, server, HEADERS_KEY, violations);
		collectUrlViolations(name, server, violations);
	}

	private void collectArgsViolations(String name, JsonNode server, List<String> violations) {
		JsonNode args = server.get(ARGS_KEY);
		if (args == null) {
			return;
		}
		if (!args.isArray()) {
			add(name, "has an 'args' that is not an array", violations);
			return;
		}
		for (int i = 0; i < args.size(); i++) {
			if (!args.get(i).isTextual()) {
				add(name, "has a non-string entry in 'args'", violations);
			}
		}
	}

	private void collectStringMapViolations(String name, JsonNode server, String key, List<String> violations) {
		JsonNode map = server.get(key);
		if (map == null) {
			return;
		}
		if (!map.isObject()) {
			add(name, "has a '" + key + "' that is not an object", violations);
			return;
		}
		for (String field : JsonNodes.fieldNames(map)) {
			if (!map.get(field).isTextual()) {
				add(name, "has a non-string value for '" + key + "." + field + "'", violations);
			}
		}
	}

	private void collectUrlViolations(String name, JsonNode server, List<String> violations) {
		JsonNode url = server.get(URL_KEY);
		if (url == null || !url.isTextual()) {
			return;
		}
		String scheme = schemeOf(url.asText());
		if (scheme == null) {
			add(name, "has a malformed 'url': " + url.asText(), violations);
		} else if (requireHttps && !scheme.equals(HTTPS_SCHEME)) {
			add(name, "must use an https 'url': " + url.asText(), violations);
		}
	}

	/** Every violation names the server whose entry is malformed. */
	private void add(String name, String problem, List<String> violations) {
		violations.add("mcp.json server '" + name + "' " + problem);
	}

	/** The {@code http}/{@code https} scheme of a syntactically valid absolute URL, or null otherwise. */
	private String schemeOf(String url) {
		try {
			URI uri = new URI(url);
			String scheme = uri.getScheme();
			if (scheme == null || uri.getHost() == null) {
				return null;
			}
			String lower = scheme.toLowerCase();
			return lower.equals(HTTP_SCHEME) || lower.equals(HTTPS_SCHEME) ? lower : null;
		} catch (URISyntaxException e) {
			return null;
		}
	}

	void setMcpFile(File mcpFile) {
		this.mcpFile = mcpFile;
	}

	void setRequireHttps(boolean requireHttps) {
		this.requireHttps = requireHttps;
	}
}

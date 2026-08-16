package io.github.adamw7.tools.enforcer.mcp;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
 * URL (and {@code https} only when {@code requireHttps} is set), unless it is
 * assembled from an environment variable expansion, which only the shell that
 * resolves it can judge; and</li>
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

	/** The keys shared with {@link McpServersValidRule}, named once in {@link McpServers}. */
	private static final String MCP_SERVERS_KEY = McpServers.SECTION_KEY;
	private static final String COMMAND_KEY = McpServers.COMMAND_KEY;
	private static final String URL_KEY = McpServers.URL_KEY;

	private static final String ARGS_KEY = "args";
	private static final String ENV_KEY = "env";
	private static final String HEADERS_KEY = "headers";
	private static final String HTTP_SCHEME = "http";
	private static final String HTTPS_SCHEME = "https";

	/** An environment variable expansion, in either spelling Claude Code resolves in {@code .mcp.json}. */
	private static final Pattern EXPANSION = Pattern.compile("\\$\\{[^}]*\\}|\\$[A-Za-z_][A-Za-z0-9_]*");

	/** The {@code .mcp.json} file to validate. Injected from the rule configuration. */
	private File mcpFile;

	/** When true, a server {@code url} must use {@code https} rather than plain {@code http}. */
	private boolean requireHttps;

	/** A project-level {@code .mcp.json} is optional in Claude Code, so an absent file is a pass. */
	public McpConfigFormatRule() {
		super("mcpFile", "mcp.json", OPTIONAL);
	}

	@Override
	protected File jsonFile() {
		return mcpFile;
	}

	@Override
	protected String header() {
		return "mcp.json server configuration is not well formed:";
	}

	@Override
	protected void collectViolations(JsonNode mcp, List<String> violations) {
		section(mcp, MCP_SERVERS_KEY, violations)
				.ifPresent(servers -> McpServers.forEach(servers,
						(name, server) -> collectServerViolations(name, server, violations)));
	}

	private void collectServerViolations(String name, JsonNode server, List<String> violations) {
		if (server == null) {
			return;
		}
		if (server.has(COMMAND_KEY) && server.has(URL_KEY)) {
			McpServers.add(name, "declares both a 'command' and a 'url'", violations);
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
			McpServers.add(name, "has an 'args' that is not an array", violations);
			return;
		}
		for (int i = 0; i < args.size(); i++) {
			if (!args.get(i).isTextual()) {
				McpServers.add(name, "has a non-string entry in 'args'", violations);
			}
		}
	}

	private void collectStringMapViolations(String name, JsonNode server, String key, List<String> violations) {
		JsonNode map = server.get(key);
		if (map == null) {
			return;
		}
		if (!map.isObject()) {
			McpServers.add(name, "has a '" + key + "' that is not an object", violations);
			return;
		}
		for (String field : JsonNodes.fieldNames(map)) {
			if (!map.get(field).isTextual()) {
				McpServers.add(name, "has a non-string value for '" + key + "." + field + "'", violations);
			}
		}
	}

	private void collectUrlViolations(String name, JsonNode server, List<String> violations) {
		JsonNode url = server.get(URL_KEY);
		if (url == null || !url.isTextual() || isExpanded(url.asText())) {
			return;
		}
		String scheme = schemeOf(url.asText());
		if (scheme == null) {
			McpServers.add(name, "has a malformed 'url': " + url.asText(), violations);
		} else if (requireHttps && !scheme.equals(HTTPS_SCHEME)) {
			McpServers.add(name, "must use an https 'url': " + url.asText(), violations);
		}
	}

	/**
	 * True when the URL is assembled at load time from an environment variable, which
	 * Claude Code expands before it ever reaches a URL parser. Only the shell knows
	 * what {@code https://${MCP_HOST}/mcp} becomes, so there is nothing here to judge
	 * — and judging it anyway reported a configuration Claude Code loads happily as
	 * malformed, against the very advice {@code noSecrets} gives for keeping a
	 * credential out of the file.
	 */
	private boolean isExpanded(String url) {
		return EXPANSION.matcher(url).find();
	}

	/**
	 * The {@code http}/{@code https} scheme of a syntactically valid absolute URL, or
	 * null otherwise. Presence of an authority is what makes the URL absolute, read
	 * from {@link URI#getAuthority()} rather than {@link URI#getHost()}: the latter is
	 * null for a host name {@code java.net.URI} considers non-compliant, so an
	 * underscore in it — routine in a container or service name — turned a URL Claude
	 * Code connects to into a reported malformation.
	 */
	private String schemeOf(String url) {
		try {
			URI uri = new URI(url);
			String scheme = uri.getScheme();
			if (scheme == null || uri.getAuthority() == null || uri.getAuthority().isBlank()) {
				return null;
			}
			String lower = scheme.toLowerCase(Locale.ROOT);
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

package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;
import io.github.adamw7.tools.enforcer.rule.Patterns;

/**
 * Enforcer rule that validates the entries of the {@code permissions} lists in
 * {@code .claude/settings.json}. Where {@link SettingsJsonValidRule} asserts
 * policy (which entries must or must not be present), this rule validates the
 * entries themselves: every value in {@code allow}, {@code deny}, and {@code ask}
 * must be a non-blank string of the form {@code Tool} or {@code Tool(specifier)},
 * because a malformed entry such as {@code Bash(mvn *} grants nothing and fails
 * silently at runtime. A duplicate within a list, and an entry that appears in
 * both {@code allow} and {@code deny}, are reported too.
 * <p>
 * When {@code allowedTools} is configured, the tool-name part of every entry must
 * be in that list, so a typo such as {@code Bsah(mvn *)} cannot slip through;
 * entries naming MCP tools (prefixed {@code mcp__}) are exempt because their names
 * are defined by the project's servers. When {@code forbiddenEntryPatterns} is
 * configured, an {@code allow} entry matching any of the regular expressions is
 * reported, so an over-broad grant such as {@code Bash(*)} can be banned by shape
 * rather than by exact spelling. A file without a {@code permissions} section
 * passes.
 */
@Named("permissionsFormat")
public class PermissionsFormatRule extends JsonFileRule {

	private static final String PERMISSIONS_KEY = "permissions";
	private static final String ALLOW_KEY = "allow";
	private static final String DENY_KEY = "deny";
	private static final List<String> LIST_KEYS = List.of(ALLOW_KEY, DENY_KEY, "ask");

	/**
	 * A built-in tool name, or an {@code mcp__server__tool} name whose server and
	 * tool parts may contain hyphens (e.g. {@code mcp__claude-code-remote__list_repos}),
	 * each optionally followed by a parenthesised specifier.
	 */
	private static final Pattern ENTRY_SYNTAX = Pattern
			.compile("(mcp__[A-Za-z0-9_-]+|[A-Za-z][A-Za-z0-9_]*)(\\(.+\\))?");
	private static final String MCP_TOOL_PREFIX = "mcp__";
	private static final String FORBIDDEN_PATTERN_PARAMETER = "forbiddenEntryPattern";

	/** The {@code .claude/settings.json} file to validate. Injected from the rule configuration. */
	private File settingsFile;

	/** Optional whitelist of tool names an entry may reference. When set, unknown tools are reported. */
	private List<String> allowedTools;

	/** Optional regular expressions no {@code allow} entry may match, e.g. {@code Bash\(\*\)}. */
	private List<String> forbiddenEntryPatterns;

	public PermissionsFormatRule() {
		super("settingsFile", "settings.json");
	}

	@Override
	protected File jsonFile() {
		return settingsFile;
	}

	@Override
	protected String header() {
		return "settings.json permissions are not well formed:";
	}

	@Override
	protected void collectViolations(JsonNode settings, List<String> violations) throws EnforcerRuleException {
		if (!settings.has(PERMISSIONS_KEY)) {
			return;
		}
		JsonNode permissions = JsonNodes.objectAt(settings, PERMISSIONS_KEY);
		if (permissions == null) {
			violations.add("settings.json 'permissions' must be a JSON object");
			return;
		}
		for (String key : LIST_KEYS) {
			collectListViolations(permissions, key, violations);
		}
		collectContradictions(permissions, violations);
		collectForbiddenEntries(permissions, violations);
	}

	private void collectListViolations(JsonNode permissions, String key, List<String> violations) {
		if (!permissions.has(key)) {
			return;
		}
		JsonNode list = JsonNodes.arrayAt(permissions, key);
		if (list == null) {
			add(key, "must be an array", violations);
			return;
		}
		Set<String> seen = new LinkedHashSet<>();
		for (int i = 0; i < list.size(); i++) {
			collectEntryViolations(list.get(i), key, i, seen, violations);
		}
	}

	private void collectEntryViolations(JsonNode entry, String key, int index, Set<String> seen,
			List<String> violations) {
		if (!entry.isTextual()) {
			add(key, "entry " + (index + 1) + " must be a string", violations);
			return;
		}
		String value = entry.asText();
		collectSyntaxViolations(value, key, violations);
		if (!seen.add(value)) {
			add(key, "lists '" + value + "' more than once", violations);
		}
		collectUnknownToolViolation(value, key, violations);
	}

	private void collectSyntaxViolations(String value, String key, List<String> violations) {
		if (value.isBlank()) {
			add(key, "contains a blank entry", violations);
		} else if (!ENTRY_SYNTAX.matcher(value).matches()) {
			add(key, "entry '" + value + "' is not of the form Tool or Tool(specifier)", violations);
		} else if (specifierOf(value).isBlank() && value.contains("(")) {
			add(key, "entry '" + value + "' has a blank specifier", violations);
		}
	}

	private void collectUnknownToolViolation(String value, String key, List<String> violations) {
		if (allowedTools == null || value.isBlank()) {
			return;
		}
		String tool = toolNameOf(value);
		if (!tool.startsWith(MCP_TOOL_PREFIX) && !allowedTools.contains(tool)) {
			add(key, "entry '" + value + "' references unknown tool '" + tool + "'", violations);
		}
	}

	private void collectContradictions(JsonNode permissions, List<String> violations) {
		Set<String> denied = textEntries(permissions, DENY_KEY);
		for (String entry : textEntries(permissions, ALLOW_KEY)) {
			if (denied.contains(entry)) {
				violations.add("settings.json permission '" + entry + "' appears in both 'allow' and 'deny'");
			}
		}
	}

	private void collectForbiddenEntries(JsonNode permissions, List<String> violations) throws EnforcerRuleException {
		if (forbiddenEntryPatterns == null) {
			return;
		}
		List<Pattern> patterns = Patterns.compileAll(forbiddenEntryPatterns, FORBIDDEN_PATTERN_PARAMETER);
		for (String entry : textEntries(permissions, ALLOW_KEY)) {
			addForbiddenEntryViolations(entry, patterns, violations);
		}
	}

	private void addForbiddenEntryViolations(String entry, List<Pattern> patterns, List<String> violations) {
		for (Pattern pattern : patterns) {
			if (pattern.matcher(entry).matches()) {
				add(ALLOW_KEY, "entry '" + entry + "' matches forbidden pattern '" + pattern + "'", violations);
			}
		}
	}

	/** The textual entries of one permission list, in document order, de-duplicated. */
	private Set<String> textEntries(JsonNode permissions, String key) {
		JsonNode list = JsonNodes.arrayAt(permissions, key);
		if (list == null) {
			return Set.of();
		}
		return list.valueStream().filter(JsonNode::isTextual).map(JsonNode::asText)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** Every violation names the permission list the entry sits in. */
	private void add(String key, String problem, List<String> violations) {
		violations.add("settings.json 'permissions." + key + "' " + problem);
	}

	/** The specifier between the parentheses, or the whole value when there are none. */
	private String specifierOf(String value) {
		int open = value.indexOf('(');
		return open < 0 ? value : value.substring(open + 1, value.length() - 1);
	}

	/** The tool-name part of an entry: everything before the opening parenthesis. */
	private String toolNameOf(String value) {
		int open = value.indexOf('(');
		return open < 0 ? value : value.substring(0, open);
	}

	void setSettingsFile(File settingsFile) {
		this.settingsFile = settingsFile;
	}

	void setAllowedTools(List<String> allowedTools) {
		this.allowedTools = allowedTools;
	}

	void setForbiddenEntryPatterns(List<String> forbiddenEntryPatterns) {
		this.forbiddenEntryPatterns = forbiddenEntryPatterns;
	}
}

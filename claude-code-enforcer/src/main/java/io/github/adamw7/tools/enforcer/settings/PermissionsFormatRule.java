package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;

/**
 * Enforcer rule that validates the entries of the {@code permissions} lists in
 * {@code .claude/settings.json}. Where {@link SettingsJsonValidRule} asserts
 * policy (which entries must or must not be present), this rule validates the
 * entries themselves: every value in {@code allow}, {@code deny}, and
 * {@code ask} must be a non-blank string of the form {@code Tool} or
 * {@code Tool(specifier)}, because a malformed entry such as {@code Bash(mvn *}
 * grants nothing and fails silently at runtime. A duplicated entry within a
 * list and an entry that appears in both {@code allow} and {@code deny} are
 * reported too, since the contradiction means one of the two is not doing what
 * its author intended.
 * <p>
 * When {@code allowedTools} is configured, the tool-name part of every entry
 * must be in that list, so a typo such as {@code Bsah(mvn *)} cannot slip
 * through; entries naming MCP tools (prefixed {@code mcp__}) are exempt because
 * their names are defined by the project's servers, not by Claude Code. When
 * {@code forbiddenEntryPatterns} is configured, an {@code allow} entry matching
 * any of the regular expressions is reported, so an over-broad grant such as
 * {@code Bash(*)} can be banned by shape rather than by exact spelling.
 * <p>
 * A settings file without a {@code permissions} section passes, since there is
 * nothing to validate. All problems found are reported together.
 */
@Named("permissionsFormat")
public class PermissionsFormatRule extends JsonFileRule {

	private static final String PERMISSIONS_KEY = "permissions";
	private static final String ALLOW_KEY = "allow";
	private static final String DENY_KEY = "deny";
	private static final List<String> LIST_KEYS = List.of(ALLOW_KEY, DENY_KEY, "ask");
	private static final Pattern ENTRY_SYNTAX = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\(.+\\))?");
	private static final String MCP_TOOL_PREFIX = "mcp__";

	/** The {@code .claude/settings.json} file to validate. Injected from the rule configuration. */
	private File settingsFile;

	/** Optional whitelist of tool names an entry may reference. When set, unknown tools are reported. */
	private List<String> allowedTools;

	/** Optional regular expressions no {@code allow} entry may match, e.g. {@code Bash\(\*\)}. */
	private List<String> forbiddenEntryPatterns;

	@Override
	protected File jsonFile() {
		return settingsFile;
	}

	@Override
	protected String fileParameter() {
		return "settingsFile";
	}

	@Override
	protected String description() {
		return "settings.json";
	}

	@Override
	protected String header() {
		return "settings.json permissions are not well formed:";
	}

	@Override
	protected void collectViolations(JsonNode settings, List<String> violations) {
		if (!settings.has(PERMISSIONS_KEY)) {
			return;
		}
		JsonNode permissions = JsonNodes.objectAt(settings, PERMISSIONS_KEY);
		if (permissions == null) {
			violations.add("settings.json 'permissions' must be a JSON object");
		} else {
			collectListViolations(permissions, violations);
		}
	}

	private void collectListViolations(JsonNode permissions, List<String> violations) {
		for (String key : LIST_KEYS) {
			collectSingleListViolations(permissions, key, violations);
		}
		collectContradictions(permissions, violations);
		collectForbiddenEntries(permissions, violations);
	}

	private void collectSingleListViolations(JsonNode permissions, String key, List<String> violations) {
		if (!permissions.has(key)) {
			return;
		}
		JsonNode list = JsonNodes.arrayAt(permissions, key);
		if (list == null) {
			violations.add("settings.json 'permissions." + key + "' must be an array");
		} else {
			collectEntryViolations(list, key, violations);
		}
	}

	private void collectEntryViolations(JsonNode list, String key, List<String> violations) {
		Set<String> seen = new LinkedHashSet<>();
		for (int i = 0; i < list.size(); i++) {
			collectEntryViolations(list.get(i), key, i, seen, violations);
		}
	}

	private void collectEntryViolations(JsonNode entry, String key, int index, Set<String> seen,
			List<String> violations) {
		if (!entry.isTextual()) {
			violations.add("settings.json 'permissions." + key + "' entry " + (index + 1) + " must be a string");
			return;
		}
		String value = entry.asText();
		collectSyntaxViolations(value, key, violations);
		collectDuplicateViolation(value, key, seen, violations);
		collectUnknownToolViolation(value, key, violations);
	}

	private void collectSyntaxViolations(String value, String key, List<String> violations) {
		if (value.isBlank()) {
			violations.add("settings.json 'permissions." + key + "' contains a blank entry");
		} else if (!ENTRY_SYNTAX.matcher(value).matches()) {
			violations.add("settings.json 'permissions." + key + "' entry '" + value
					+ "' is not of the form Tool or Tool(specifier)");
		} else if (specifierOf(value).isBlank() && value.contains("(")) {
			violations.add("settings.json 'permissions." + key + "' entry '" + value + "' has a blank specifier");
		}
	}

	private void collectDuplicateViolation(String value, String key, Set<String> seen, List<String> violations) {
		if (!seen.add(value)) {
			violations.add("settings.json 'permissions." + key + "' lists '" + value + "' more than once");
		}
	}

	private void collectUnknownToolViolation(String value, String key, List<String> violations) {
		if (allowedTools == null || value.isBlank()) {
			return;
		}
		String tool = toolNameOf(value);
		if (!tool.startsWith(MCP_TOOL_PREFIX) && !allowedTools.contains(tool)) {
			violations.add("settings.json 'permissions." + key + "' entry '" + value
					+ "' references unknown tool '" + tool + "'");
		}
	}

	private void collectContradictions(JsonNode permissions, List<String> violations) {
		Set<String> denied = textEntries(permissions, DENY_KEY);
		for (String entry : textEntries(permissions, ALLOW_KEY)) {
			addContradictionViolation(entry, denied, violations);
		}
	}

	private void addContradictionViolation(String entry, Set<String> denied, List<String> violations) {
		if (denied.contains(entry)) {
			violations.add("settings.json permission '" + entry + "' appears in both 'allow' and 'deny'");
		}
	}

	private void collectForbiddenEntries(JsonNode permissions, List<String> violations) {
		if (forbiddenEntryPatterns == null) {
			return;
		}
		List<Pattern> patterns = forbiddenEntryPatterns.stream().map(Pattern::compile).toList();
		for (String entry : textEntries(permissions, ALLOW_KEY)) {
			addForbiddenEntryViolations(entry, patterns, violations);
		}
	}

	private void addForbiddenEntryViolations(String entry, List<Pattern> patterns, List<String> violations) {
		for (Pattern pattern : patterns) {
			if (pattern.matcher(entry).matches()) {
				violations.add("settings.json 'permissions.allow' entry '" + entry
						+ "' matches forbidden pattern '" + pattern + "'");
			}
		}
	}

	private Set<String> textEntries(JsonNode permissions, String key) {
		JsonNode list = JsonNodes.arrayAt(permissions, key);
		Set<String> entries = new LinkedHashSet<>();
		int size = list != null ? list.size() : 0;
		for (int i = 0; i < size; i++) {
			addTextEntry(list.get(i), entries);
		}
		return entries;
	}

	private void addTextEntry(JsonNode entry, Set<String> entries) {
		if (entry.isTextual()) {
			entries.add(entry.asText());
		}
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

	@Override
	public String toString() {
		return String.format("PermissionsFormatRule[settingsFile=%s]", settingsFile);
	}
}

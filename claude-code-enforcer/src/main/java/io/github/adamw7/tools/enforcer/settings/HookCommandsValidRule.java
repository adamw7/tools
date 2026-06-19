package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that fails the build when the {@code hooks} section of
 * {@code .claude/settings.json} is malformed. The file must exist, be non-empty,
 * and parse as JSON. When a {@code hooks} object is present, every event must map
 * to an array of groups, every group must carry a {@code hooks} array, and every
 * hook in it must declare a non-blank {@code type}; a {@code command} hook must
 * also declare a non-blank {@code command}.
 * <p>
 * A command that points at a project-local script through the
 * {@code $CLAUDE_PROJECT_DIR} variable is resolved against {@code projectDir}
 * (the parent of the settings file's directory by default) and must exist on
 * disk, so a renamed or missing hook script such as
 * {@code $CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh} is caught. This
 * check is on by default and can be switched off with
 * {@code validateScriptReferences}. When {@code allowedEvents} is configured, any
 * event name outside that set is reported, which catches a mistyped
 * {@code SessionSart}.
 * <p>
 * A settings file without a {@code hooks} object is allowed; all problems found
 * are reported together.
 */
@Named("hookCommandsValid")
public class HookCommandsValidRule extends ClaudeCodeEnforcerRule {

	private static final String HOOKS_KEY = "hooks";
	private static final String TYPE_KEY = "type";
	private static final String COMMAND_KEY = "command";
	private static final String COMMAND_TYPE = "command";
	private static final String PROJECT_DIR_BRACED = "${CLAUDE_PROJECT_DIR}";
	private static final String PROJECT_DIR_PLAIN = "$CLAUDE_PROJECT_DIR";

	/** The {@code .claude/settings.json} file to validate. Injected from the rule configuration. */
	private File settingsFile;

	/** Base directory that {@code $CLAUDE_PROJECT_DIR} resolves to. Defaults to the settings file's grandparent. */
	private File projectDir;

	/** Optional whitelist of hook event names. When set, unknown events are reported. */
	private List<String> allowedEvents;

	/** When true (default), a {@code $CLAUDE_PROJECT_DIR} script reference must resolve to an existing file. */
	private boolean validateScriptReferences = true;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		verifyFile();
		String content = readContent();
		List<String> violations = new ArrayList<>();
		parseAndCollect(content, violations);
		report("settings.json hooks are not well formed:", violations);
	}

	private void verifyConfigured() throws EnforcerRuleException {
		if (settingsFile == null) {
			throw new EnforcerRuleException("The settingsFile parameter is not configured");
		}
	}

	private void verifyFile() throws EnforcerRuleException {
		if (!settingsFile.isFile()) {
			throw new EnforcerRuleException("settings.json does not exist at " + settingsFile);
		}
	}

	private String readContent() throws EnforcerRuleException {
		String content = MarkdownText.read(settingsFile, "settings.json");
		if (content.isBlank()) {
			throw new EnforcerRuleException("settings.json is empty: " + settingsFile);
		}
		return content;
	}

	private void parseAndCollect(String content, List<String> violations) {
		JSONObject settings = parse(content, violations);
		if (settings != null) {
			collectHookViolations(settings, violations);
		}
	}

	private JSONObject parse(String content, List<String> violations) {
		try {
			return new JSONObject(content);
		} catch (JSONException e) {
			violations.add("settings.json is not valid JSON: " + e.getMessage());
			return null;
		}
	}

	private void collectHookViolations(JSONObject settings, List<String> violations) {
		JSONObject hooks = settings.optJSONObject(HOOKS_KEY);
		if (hooks == null) {
			return;
		}
		for (String event : hooks.keySet()) {
			collectEventViolations(event, hooks, violations);
		}
	}

	private void collectEventViolations(String event, JSONObject hooks, List<String> violations) {
		addEventNameViolation(event, violations);
		JSONArray groups = hooks.optJSONArray(event);
		if (groups == null) {
			violations.add("hook event '" + event + "' must be a JSON array");
		} else {
			collectGroupsViolations(event, groups, violations);
		}
	}

	private void addEventNameViolation(String event, List<String> violations) {
		if (allowedEvents != null && !allowedEvents.contains(event)) {
			violations.add("hook event '" + event + "' is not an allowed event");
		}
	}

	private void collectGroupsViolations(String event, JSONArray groups, List<String> violations) {
		for (int i = 0; i < groups.length(); i++) {
			collectGroupViolations(event, groups.optJSONObject(i), violations);
		}
	}

	private void collectGroupViolations(String event, JSONObject group, List<String> violations) {
		if (group == null) {
			violations.add("hook event '" + event + "' has an entry that is not a JSON object");
		} else {
			collectEntriesViolations(event, group, violations);
		}
	}

	private void collectEntriesViolations(String event, JSONObject group, List<String> violations) {
		JSONArray entries = group.optJSONArray(HOOKS_KEY);
		if (entries == null) {
			violations.add("hook event '" + event + "' entry is missing a 'hooks' array");
		} else {
			collectEntriesViolations(event, entries, violations);
		}
	}

	private void collectEntriesViolations(String event, JSONArray entries, List<String> violations) {
		for (int i = 0; i < entries.length(); i++) {
			collectEntryViolations(event, entries.optJSONObject(i), violations);
		}
	}

	private void collectEntryViolations(String event, JSONObject entry, List<String> violations) {
		if (entry == null) {
			violations.add("hook event '" + event + "' has a hook that is not a JSON object");
		} else {
			collectTypedEntryViolations(event, entry, violations);
		}
	}

	private void collectTypedEntryViolations(String event, JSONObject entry, List<String> violations) {
		String type = entry.optString(TYPE_KEY, "").strip();
		if (type.isBlank()) {
			violations.add("hook event '" + event + "' has a hook missing 'type'");
		} else if (type.equals(COMMAND_TYPE)) {
			collectCommandViolations(event, entry, violations);
		}
	}

	private void collectCommandViolations(String event, JSONObject entry, List<String> violations) {
		String command = entry.optString(COMMAND_KEY, "").strip();
		if (command.isBlank()) {
			violations.add("hook event '" + event + "' has a command hook with an empty 'command'");
		} else {
			addScriptReferenceViolation(event, command, violations);
		}
	}

	private void addScriptReferenceViolation(String event, String command, List<String> violations) {
		if (!validateScriptReferences) {
			return;
		}
		String script = localScriptPath(command);
		if (script != null && !new File(script).isFile()) {
			violations.add("hook event '" + event + "' references a missing script: " + script);
		}
	}

	/** The resolved on-disk path of a {@code $CLAUDE_PROJECT_DIR}-rooted token, or null when none is referenced. */
	private String localScriptPath(String command) {
		for (String token : command.split("\\s+")) {
			String expanded = expand(token);
			if (expanded != null) {
				return expanded;
			}
		}
		return null;
	}

	private String expand(String token) {
		if (token.contains(PROJECT_DIR_BRACED)) {
			return token.replace(PROJECT_DIR_BRACED, projectDir().getPath());
		}
		if (token.contains(PROJECT_DIR_PLAIN)) {
			return token.replace(PROJECT_DIR_PLAIN, projectDir().getPath());
		}
		return null;
	}

	private File projectDir() {
		if (projectDir != null) {
			return projectDir;
		}
		File claudeDir = settingsFile.getAbsoluteFile().getParentFile();
		File root = claudeDir != null ? claudeDir.getParentFile() : null;
		return root != null ? root : new File(".");
	}

	void setSettingsFile(File settingsFile) {
		this.settingsFile = settingsFile;
	}

	void setProjectDir(File projectDir) {
		this.projectDir = projectDir;
	}

	void setAllowedEvents(List<String> allowedEvents) {
		this.allowedEvents = allowedEvents;
	}

	void setValidateScriptReferences(boolean validateScriptReferences) {
		this.validateScriptReferences = validateScriptReferences;
	}

	@Override
	public String toString() {
		return String.format("HookCommandsValidRule[settingsFile=%s]", settingsFile);
	}
}

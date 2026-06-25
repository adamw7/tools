package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;

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
public class HookCommandsValidRule extends JsonFileRule {

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
		return "settings.json hooks are not well formed:";
	}

	@Override
	protected void collectViolations(JsonNode settings, List<String> violations) {
		collectHookViolations(settings, violations);
	}

	private void collectHookViolations(JsonNode settings, List<String> violations) {
		JsonNode hooks = JsonNodes.objectAt(settings, HOOKS_KEY);
		if (hooks == null) {
			return;
		}
		for (String event : JsonNodes.fieldNames(hooks)) {
			collectEventViolations(event, hooks, violations);
		}
	}

	private void collectEventViolations(String event, JsonNode hooks, List<String> violations) {
		addEventNameViolation(event, violations);
		JsonNode groups = JsonNodes.arrayAt(hooks, event);
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

	private void collectGroupsViolations(String event, JsonNode groups, List<String> violations) {
		for (int i = 0; i < groups.size(); i++) {
			collectGroupViolations(event, JsonNodes.objectAt(groups, i), violations);
		}
	}

	private void collectGroupViolations(String event, JsonNode group, List<String> violations) {
		if (group == null) {
			violations.add("hook event '" + event + "' has an entry that is not a JSON object");
		} else {
			collectEntriesViolations(event, group, violations);
		}
	}

	private void collectEntriesViolations(String event, JsonNode group, List<String> violations) {
		JsonNode entries = JsonNodes.arrayAt(group, HOOKS_KEY);
		if (entries == null) {
			violations.add("hook event '" + event + "' entry is missing a 'hooks' array");
		} else {
			collectHookEntries(event, entries, violations);
		}
	}

	private void collectHookEntries(String event, JsonNode entries, List<String> violations) {
		for (int i = 0; i < entries.size(); i++) {
			collectEntryViolations(event, JsonNodes.objectAt(entries, i), violations);
		}
	}

	private void collectEntryViolations(String event, JsonNode entry, List<String> violations) {
		if (entry == null) {
			violations.add("hook event '" + event + "' has a hook that is not a JSON object");
		} else {
			collectTypedEntryViolations(event, entry, violations);
		}
	}

	private void collectTypedEntryViolations(String event, JsonNode entry, List<String> violations) {
		String type = JsonNodes.textAt(entry, TYPE_KEY, "").strip();
		if (type.isBlank()) {
			violations.add("hook event '" + event + "' has a hook missing 'type'");
		} else if (type.equals(COMMAND_TYPE)) {
			collectCommandViolations(event, entry, violations);
		}
	}

	private void collectCommandViolations(String event, JsonNode entry, List<String> violations) {
		String command = JsonNodes.textAt(entry, COMMAND_KEY, "").strip();
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
		if (script != null && !new File(script).exists()) {
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

package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;

/**
 * Enforcer rule that fails the build when the {@code hooks} section of
 * {@code .claude/settings.json} is malformed. When a {@code hooks} object is
 * present, every event must map to an array of groups, every group must carry a
 * {@code hooks} array, and every hook in it must declare a non-blank {@code type};
 * a {@code command} hook must also declare a non-blank {@code command}.
 * <p>
 * A command that points at a project-local script through the
 * {@code $CLAUDE_PROJECT_DIR} variable is resolved against {@code projectDir} (the
 * parent of the settings file's directory by default) and must exist on disk, so a
 * renamed or missing hook script is caught; the check is on by default and can be
 * switched off with {@code validateScriptReferences}. An event name outside a
 * configured {@code allowedEvents} is reported, which catches a mistyped
 * {@code SessionSart}. A settings file without a {@code hooks} key is allowed, but
 * a {@code hooks} that is present and is not an object is reported rather than
 * skipped, so a mistyped section cannot pass unvalidated.
 */
@Named("hookCommandsValid")
public class HookCommandsValidRule extends JsonFileRule {

	private static final String HOOKS_KEY = "hooks";
	private static final String TYPE_KEY = "type";
	private static final String COMMAND_KEY = "command";
	private static final String COMMAND_TYPE = "command";

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
		if (!settings.has(HOOKS_KEY)) {
			return;
		}
		JsonNode hooks = JsonNodes.objectAt(settings, HOOKS_KEY);
		if (hooks == null) {
			violations.add("settings.json 'hooks' must be a JSON object");
			return;
		}
		for (String event : JsonNodes.fieldNames(hooks)) {
			collectEventViolations(event, JsonNodes.arrayAt(hooks, event), violations);
		}
	}

	private void collectEventViolations(String event, JsonNode groups, List<String> violations) {
		if (allowedEvents != null && !allowedEvents.contains(event)) {
			add(event, "is not an allowed event", violations);
		}
		if (groups == null) {
			add(event, "must be a JSON array", violations);
			return;
		}
		for (int i = 0; i < groups.size(); i++) {
			collectGroupViolations(event, JsonNodes.objectAt(groups, i), violations);
		}
	}

	private void collectGroupViolations(String event, JsonNode group, List<String> violations) {
		JsonNode entries = group != null ? JsonNodes.arrayAt(group, HOOKS_KEY) : null;
		if (group == null) {
			add(event, "has an entry that is not a JSON object", violations);
		} else if (entries == null) {
			add(event, "entry is missing a 'hooks' array", violations);
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
			add(event, "has a hook that is not a JSON object", violations);
			return;
		}
		String type = JsonNodes.textAt(entry, TYPE_KEY, "").strip();
		if (type.isBlank()) {
			add(event, "has a hook missing 'type'", violations);
		} else if (type.equals(COMMAND_TYPE)) {
			collectCommandViolations(event, entry, violations);
		}
	}

	private void collectCommandViolations(String event, JsonNode entry, List<String> violations) {
		String command = JsonNodes.textAt(entry, COMMAND_KEY, "").strip();
		if (command.isBlank()) {
			add(event, "has a command hook with an empty 'command'", violations);
			return;
		}
		for (String script : localScriptPaths(command)) {
			collectMissingScriptViolation(event, script, violations);
		}
	}

	private void collectMissingScriptViolation(String event, String script, List<String> violations) {
		if (!new File(script).exists()) {
			add(event, "references a missing script: " + script, violations);
		}
	}

	/** Every violation names the event whose hook is malformed. */
	private void add(String event, String problem, List<String> violations) {
		violations.add("hook event '" + event + "' " + problem);
	}

	/**
	 * The resolved on-disk paths of every {@code $CLAUDE_PROJECT_DIR}-rooted token
	 * in the command. A command chains more than one script often enough — an
	 * {@code &&} between two hooks — that stopping at the first reference would
	 * leave the rest unchecked.
	 */
	private List<String> localScriptPaths(String command) {
		if (!validateScriptReferences) {
			return List.of();
		}
		ClaudeProjectDir projectDirs = new ClaudeProjectDir(projectDir, settingsFile);
		return CommandTokens.of(command).stream().map(projectDirs::expand).filter(Objects::nonNull).toList();
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

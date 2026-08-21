package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonNodes;

/**
 * Enforcer rule that fails the build when the {@code hooks} section of
 * {@code .claude/settings.json} is malformed. When a {@code hooks} object is
 * present, every event must map to an array of groups, every group must carry a
 * {@code hooks} array, and every hook in it must declare a non-blank {@code type}
 * as a JSON string; a {@code command} hook must also declare a non-blank
 * {@code command}, likewise as a string.
 * <p>
 * A command pointing at a project-local script — through {@code $CLAUDE_PROJECT_DIR}
 * or as the repository-relative path Claude Code resolves the same way — is resolved
 * against {@code projectDir} (the settings file's grandparent by default) and must
 * exist, so a renamed hook script is caught; switch it off with
 * {@code validateScriptReferences}. Only the program of each chained command is read
 * as a script, never an argument, so a hook passing a path it is about to write need
 * not exist. An event outside {@code allowedEvents} is reported, catching a mistyped
 * {@code SessionSart}. A file with no {@code hooks} key passes, but a {@code hooks}
 * that is present and is not an object is reported rather than skipped.
 */
@Named("hookCommandsValid")
public class HookCommandsValidRule extends SettingsJsonRule {

	/** The keys of the hooks section, named once in {@link HookCommands} and read the same way here. */
	private static final String HOOKS_KEY = HookCommands.HOOKS_KEY;
	private static final String TYPE_KEY = HookCommands.TYPE_KEY;
	private static final String COMMAND_KEY = HookCommands.COMMAND_KEY;
	private static final String COMMAND_TYPE = HookCommands.COMMAND_TYPE;

	/** Base directory that {@code $CLAUDE_PROJECT_DIR} resolves to. Defaults to the settings file's grandparent. */
	private File projectDir;

	/** Optional whitelist of hook event names. When set, unknown events are reported. */
	private List<String> allowedEvents;

	/** When true (default), a {@code $CLAUDE_PROJECT_DIR} script reference must resolve to an existing file. */
	private boolean validateScriptReferences = true;

	@Override
	protected String header() {
		return "settings.json hooks are not well formed:";
	}

	@Override
	protected void collectViolations(JsonNode settings, List<String> violations) {
		section(settings, HOOKS_KEY, violations).ifPresent(hooks -> collectHookViolations(hooks, violations));
	}

	private void collectHookViolations(JsonNode hooks, List<String> violations) {
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

	/**
	 * A {@code type} that is not a string is reported as the type error it is: a hook
	 * declaring {@code "type": 123} read as {@code "123"} matched nothing and skipped
	 * every command check in silence.
	 */
	private void collectEntryViolations(String event, JsonNode entry, List<String> violations) {
		if (entry == null) {
			add(event, "has a hook that is not a JSON object", violations);
			return;
		}
		if (JsonNodes.declaresNonText(entry, TYPE_KEY)) {
			add(event, "has a hook whose 'type' is not a string", violations);
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
		if (JsonNodes.declaresNonText(entry, COMMAND_KEY)) {
			add(event, "has a command hook whose 'command' is not a string", violations);
			return;
		}
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

	/** The resolved on-disk paths of the project-local scripts the command runs. */
	private List<String> localScriptPaths(String command) {
		if (!validateScriptReferences) {
			return List.of();
		}
		return new ClaudeProjectDir(projectDir, jsonFile()).scriptsIn(command);
	}

	public void setProjectDir(File projectDir) {
		this.projectDir = projectDir;
	}

	void setAllowedEvents(List<String> allowedEvents) {
		this.allowedEvents = allowedEvents;
	}

	void setValidateScriptReferences(boolean validateScriptReferences) {
		this.validateScriptReferences = validateScriptReferences;
	}
}

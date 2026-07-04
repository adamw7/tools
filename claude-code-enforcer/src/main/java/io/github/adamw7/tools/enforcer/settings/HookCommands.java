package io.github.adamw7.tools.enforcer.settings;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonNodes;

/**
 * Collects the command string of every {@code command}-type hook declared in a
 * parsed {@code settings.json}. It walks the
 * {@code hooks -> event -> groups -> hooks -> entry} structure defensively, so a
 * node of the wrong shape is skipped rather than throwing; validating that shape
 * is {@link HookCommandsValidRule}'s job, not this collector's.
 */
final class HookCommands {

	private static final String HOOKS_KEY = "hooks";
	private static final String TYPE_KEY = "type";
	private static final String COMMAND_KEY = "command";
	private static final String COMMAND_TYPE = "command";

	private HookCommands() {
	}

	static List<String> from(JsonNode settings) {
		List<String> commands = new ArrayList<>();
		JsonNode hooks = JsonNodes.objectAt(settings, HOOKS_KEY);
		if (hooks != null) {
			collectEvents(hooks, commands);
		}
		return commands;
	}

	private static void collectEvents(JsonNode hooks, List<String> commands) {
		for (String event : JsonNodes.fieldNames(hooks)) {
			collectGroups(JsonNodes.arrayAt(hooks, event), commands);
		}
	}

	private static void collectGroups(JsonNode groups, List<String> commands) {
		if (groups == null) {
			return;
		}
		for (int i = 0; i < groups.size(); i++) {
			collectEntries(JsonNodes.objectAt(groups, i), commands);
		}
	}

	private static void collectEntries(JsonNode group, List<String> commands) {
		if (group == null) {
			return;
		}
		JsonNode entries = JsonNodes.arrayAt(group, HOOKS_KEY);
		if (entries == null) {
			return;
		}
		for (int i = 0; i < entries.size(); i++) {
			collectCommand(JsonNodes.objectAt(entries, i), commands);
		}
	}

	private static void collectCommand(JsonNode entry, List<String> commands) {
		if (entry != null && COMMAND_TYPE.equals(JsonNodes.textAt(entry, TYPE_KEY, "").strip())) {
			commands.add(JsonNodes.textAt(entry, COMMAND_KEY, "").strip());
		}
	}
}

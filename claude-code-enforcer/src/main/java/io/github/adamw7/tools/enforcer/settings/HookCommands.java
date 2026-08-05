package io.github.adamw7.tools.enforcer.settings;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

	/*
	 * The keys of the hooks section, shared with HookCommandsValidRule: this
	 * collector reads the structure that rule validates, so the two must name it
	 * identically or one would check a section the other never sees.
	 */
	static final String HOOKS_KEY = "hooks";
	static final String TYPE_KEY = "type";
	static final String COMMAND_KEY = "command";
	static final String COMMAND_TYPE = "command";

	private HookCommands() {
	}

	static List<String> from(JsonNode settings) {
		JsonNode hooks = JsonNodes.objectAt(settings, HOOKS_KEY);
		if (hooks == null) {
			return List.of();
		}
		return JsonNodes.fieldNames(hooks).stream()
				.flatMap(event -> objects(JsonNodes.arrayAt(hooks, event)))
				.flatMap(group -> objects(JsonNodes.arrayAt(group, HOOKS_KEY)))
				.filter(HookCommands::isCommandHook)
				.map(entry -> JsonNodes.textAt(entry, COMMAND_KEY, "").strip())
				.toList();
	}

	/** The object elements of an array node, skipping an absent array and any element of another shape. */
	private static Stream<JsonNode> objects(JsonNode array) {
		if (array == null) {
			return Stream.empty();
		}
		return IntStream.range(0, array.size())
				.mapToObj(index -> JsonNodes.objectAt(array, index))
				.filter(Objects::nonNull);
	}

	private static boolean isCommandHook(JsonNode entry) {
		return COMMAND_TYPE.equals(JsonNodes.textAt(entry, TYPE_KEY, "").strip());
	}
}

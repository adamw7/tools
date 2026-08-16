package io.github.adamw7.tools.enforcer.mcp;

import java.util.List;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonNodes;

/**
 * The {@code mcpServers} vocabulary both {@code .mcp.json} rules read: how the
 * declared servers are walked, and how a violation names the one it found. The two
 * rules check different things about a server — {@link McpServersValidRule} its
 * transport, {@link McpConfigFormatRule} the fields around it — but they address
 * the same section the same way, so the traversal and the message prefix live here
 * rather than once in each.
 */
final class McpServers {

	/*
	 * The keys of a server entry both rules read: one validates the transport those
	 * two declare, the other the fields around them, so the two must name them
	 * identically or one would check a key the other never sees.
	 */
	static final String SECTION_KEY = "mcpServers";
	static final String COMMAND_KEY = "command";
	static final String URL_KEY = "url";

	private McpServers() {
	}

	/**
	 * Hands each declared server to {@code check}, by name. An entry that is not a
	 * JSON object arrives as {@code null}, since what to say about one differs
	 * between the rules: a malformed entry is a violation to the rule that validates
	 * the transport, and nothing to say for the rule that validates optional fields
	 * the entry cannot have.
	 */
	static void forEach(JsonNode servers, BiConsumer<String, JsonNode> check) {
		for (String name : JsonNodes.fieldNames(servers)) {
			check.accept(name, JsonNodes.objectAt(servers, name));
		}
	}

	/** Collects one violation, named after the server whose entry is malformed. */
	static void add(String name, String problem, List<String> violations) {
		violations.add("mcp.json server '" + name + "' " + problem);
	}
}

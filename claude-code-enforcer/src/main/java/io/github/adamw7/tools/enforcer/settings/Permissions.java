package io.github.adamw7.tools.enforcer.settings;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonNodes;

/**
 * The {@code permissions} vocabulary both settings rules read: the section, its
 * three lists, and what counts as an entry of one. {@link SettingsJsonValidRule}
 * asserts policy on the {@code allow} list while {@link PermissionsFormatRule}
 * validates the entries of all three, so the two must name the section the same way
 * and read an entry the same way — or one would assert policy on a list the other
 * never validated.
 * <p>
 * An entry is a string and only a string. A list element of any other type is a
 * malformation {@code permissionsFormat} reports in its own right, and reading it
 * as its text besides let a JSON {@code 123} answer for a permission
 * {@code settingsJsonValid} was configured to require, which no settings file can
 * actually grant.
 */
final class Permissions {

	static final String SECTION_KEY = "permissions";
	static final String ALLOW_KEY = "allow";
	static final String DENY_KEY = "deny";
	static final String ASK_KEY = "ask";

	private Permissions() {
	}

	/** The textual entries of one permission list, in document order, de-duplicated. */
	static Set<String> entriesIn(JsonNode permissions, String key) {
		JsonNode list = JsonNodes.arrayAt(permissions, key);
		if (list == null) {
			return Set.of();
		}
		return list.valueStream().filter(JsonNode::isTextual).map(JsonNode::asText)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}
}

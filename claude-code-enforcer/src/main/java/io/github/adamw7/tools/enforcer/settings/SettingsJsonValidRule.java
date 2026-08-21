package io.github.adamw7.tools.enforcer.settings;

import java.util.List;
import java.util.Set;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonNodes;
import io.github.adamw7.tools.enforcer.rule.Violations;

/**
 * Enforcer rule that fails the build when {@code .claude/settings.json} is
 * missing, empty, or not valid JSON. Beyond that baseline it can assert policy
 * on the {@code permissions.allow} list: {@code requiredPermissions} must all be
 * present and {@code forbiddenPermissions} must all be absent, so a project can
 * mandate a permission it relies on or ban an over-broad wildcard such as
 * {@code Bash(*)}.
 * <p>
 * Both policy lists are optional and empty by default, so a project that only
 * wants the JSON parsed needs no further configuration. All problems found are
 * reported together.
 */
@Named("settingsJsonValid")
public class SettingsJsonValidRule extends SettingsJsonRule {

	/** Permission entries that must appear in {@code permissions.allow}. */
	private List<String> requiredPermissions;

	/** Permission entries that must not appear in {@code permissions.allow}. */
	private List<String> forbiddenPermissions;

	@Override
	protected void collectViolations(JsonNode settings, List<String> violations) {
		if (requiredPermissions == null && forbiddenPermissions == null) {
			return;
		}
		Set<String> allow = allowList(settings);
		Violations.each(requiredPermissions, permission -> !allow.contains(permission),
				permission -> "settings.json is missing required permission: " + permission, violations);
		Violations.each(forbiddenPermissions, allow::contains,
				permission -> "settings.json contains forbidden permission: " + permission, violations);
	}

	/** The granted permissions, read exactly as {@code permissionsFormat} reads the same list. */
	private Set<String> allowList(JsonNode settings) {
		JsonNode permissions = JsonNodes.objectAt(settings, Permissions.SECTION_KEY);
		return permissions != null ? Permissions.entriesIn(permissions, Permissions.ALLOW_KEY) : Set.of();
	}

	void setRequiredPermissions(List<String> requiredPermissions) {
		this.requiredPermissions = requiredPermissions;
	}

	void setForbiddenPermissions(List<String> forbiddenPermissions) {
		this.forbiddenPermissions = forbiddenPermissions;
	}
}

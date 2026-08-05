package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.List;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;
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
public class SettingsJsonValidRule extends JsonFileRule {

	private static final String PERMISSIONS_KEY = "permissions";
	private static final String ALLOW_KEY = "allow";

	/** The {@code .claude/settings.json} file to validate. Injected from the rule configuration. */
	private File settingsFile;

	/** Permission entries that must appear in {@code permissions.allow}. */
	private List<String> requiredPermissions;

	/** Permission entries that must not appear in {@code permissions.allow}. */
	private List<String> forbiddenPermissions;

	public SettingsJsonValidRule() {
		super("settingsFile", "settings.json");
	}

	@Override
	protected File jsonFile() {
		return settingsFile;
	}

	@Override
	protected void collectViolations(JsonNode settings, List<String> violations) {
		if (requiredPermissions == null && forbiddenPermissions == null) {
			return;
		}
		List<String> allow = allowList(settings);
		Violations.each(requiredPermissions, permission -> !allow.contains(permission),
				permission -> "settings.json is missing required permission: " + permission, violations);
		Violations.each(forbiddenPermissions, allow::contains,
				permission -> "settings.json contains forbidden permission: " + permission, violations);
	}

	private List<String> allowList(JsonNode settings) {
		JsonNode permissions = JsonNodes.objectAt(settings, PERMISSIONS_KEY);
		JsonNode allow = permissions != null ? JsonNodes.arrayAt(permissions, ALLOW_KEY) : null;
		return allow != null ? allow.valueStream().map(JsonNode::asText).toList() : List.of();
	}

	void setSettingsFile(File settingsFile) {
		this.settingsFile = settingsFile;
	}

	void setRequiredPermissions(List<String> requiredPermissions) {
		this.requiredPermissions = requiredPermissions;
	}

	void setForbiddenPermissions(List<String> forbiddenPermissions) {
		this.forbiddenPermissions = forbiddenPermissions;
	}
}

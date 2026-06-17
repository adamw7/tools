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
public class SettingsJsonValidRule extends ClaudeCodeEnforcerRule {

	private static final String PERMISSIONS_KEY = "permissions";
	private static final String ALLOW_KEY = "allow";

	/** The {@code .claude/settings.json} file to validate. Injected from the rule configuration. */
	private File settingsFile;

	/** Permission entries that must appear in {@code permissions.allow}. */
	private List<String> requiredPermissions;

	/** Permission entries that must not appear in {@code permissions.allow}. */
	private List<String> forbiddenPermissions;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		verifyFile();
		String content = readContent();
		List<String> violations = new ArrayList<>();
		parseAndCollect(content, violations);
		report("settings.json is not well formed:", violations);
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
			collectPermissionViolations(settings, violations);
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

	private void collectPermissionViolations(JSONObject settings, List<String> violations) {
		if (requiredPermissions == null && forbiddenPermissions == null) {
			return;
		}
		List<String> allow = allowList(settings);
		collectRequiredPermissions(allow, violations);
		collectForbiddenPermissions(allow, violations);
	}

	private List<String> allowList(JSONObject settings) {
		JSONObject permissions = settings.optJSONObject(PERMISSIONS_KEY);
		JSONArray allow = permissions != null ? permissions.optJSONArray(ALLOW_KEY) : null;
		return allow != null ? toStringList(allow) : List.of();
	}

	private List<String> toStringList(JSONArray array) {
		List<String> entries = new ArrayList<>();
		for (int i = 0; i < array.length(); i++) {
			entries.add(array.getString(i));
		}
		return entries;
	}

	private void collectRequiredPermissions(List<String> allow, List<String> violations) {
		if (requiredPermissions == null) {
			return;
		}
		for (String permission : requiredPermissions) {
			if (!allow.contains(permission)) {
				violations.add("settings.json is missing required permission: " + permission);
			}
		}
	}

	private void collectForbiddenPermissions(List<String> allow, List<String> violations) {
		if (forbiddenPermissions == null) {
			return;
		}
		for (String permission : forbiddenPermissions) {
			if (allow.contains(permission)) {
				violations.add("settings.json contains forbidden permission: " + permission);
			}
		}
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

	@Override
	public String toString() {
		return String.format("SettingsJsonValidRule[settingsFile=%s]", settingsFile);
	}
}

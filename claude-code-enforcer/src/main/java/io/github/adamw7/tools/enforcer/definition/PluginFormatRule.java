package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;
import io.github.adamw7.tools.enforcer.text.NameConvention;

/**
 * Enforcer rule that validates a Claude Code plugin manifest, the
 * {@code .claude-plugin/plugin.json} of a plugin repository. The manifest names
 * the plugin in every marketplace listing, so a malformed one breaks
 * installation rather than the build that produced it. When present, the file
 * must be non-empty valid JSON declaring every required key ({@code name} by
 * default, overridable via {@code requiredKeys}); the {@code name} is held to
 * the Claude Code naming convention (lower-case kebab-case, at most
 * {@value NameConvention#MAX_LENGTH} characters), a {@code version} must be a
 * dotted number with an optional pre-release suffix, and a {@code description}
 * must be non-empty. When {@code allowedKeys} is configured, any key outside
 * that set is reported, which catches typos such as {@code descripton}.
 * <p>
 * Not every repository ships a plugin, so an absent manifest is a pass; the
 * rule only fails when the file is present and malformed. All problems found
 * are reported together.
 */
@Named("pluginFormat")
public class PluginFormatRule extends JsonFileRule {

	private static final String NAME_KEY = "name";
	private static final String VERSION_KEY = "version";
	private static final String DESCRIPTION_KEY = "description";
	private static final List<String> DEFAULT_REQUIRED_KEYS = List.of(NAME_KEY);
	private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+){0,2}([-+][A-Za-z0-9.-]+)?");

	/** The {@code .claude-plugin/plugin.json} manifest to validate. Injected from the rule configuration. */
	private File pluginFile;

	/** Optional override for the required manifest keys. */
	private List<String> requiredKeys;

	/** Optional whitelist of allowed manifest keys. When set, unknown keys are reported. */
	private List<String> allowedKeys;

	@Override
	protected File jsonFile() {
		return pluginFile;
	}

	@Override
	protected String fileParameter() {
		return "pluginFile";
	}

	@Override
	protected String description() {
		return "plugin.json";
	}

	@Override
	protected String header() {
		return "plugin.json is not well formed:";
	}

	/** Not every repository ships a plugin, so an absent manifest is a pass. */
	@Override
	protected void handleMissingFile(File file) {
	}

	@Override
	protected void collectViolations(JsonNode manifest, List<String> violations) {
		collectMissingKeys(manifest, violations);
		collectUnknownKeys(manifest, violations);
		collectNameViolations(manifest, violations);
		collectVersionViolation(manifest, violations);
		collectDescriptionViolation(manifest, violations);
	}

	private void collectMissingKeys(JsonNode manifest, List<String> violations) {
		for (String key : requiredKeys()) {
			if (!manifest.has(key)) {
				violations.add("plugin.json is missing required key '" + key + "' in: " + pluginFile);
			}
		}
	}

	private void collectUnknownKeys(JsonNode manifest, List<String> violations) {
		if (allowedKeys == null) {
			return;
		}
		for (String key : JsonNodes.fieldNames(manifest)) {
			addUnknownKeyViolation(key, violations);
		}
	}

	private void addUnknownKeyViolation(String key, List<String> violations) {
		if (!allowedKeys.contains(key)) {
			violations.add("plugin.json has unknown key '" + key + "' in: " + pluginFile);
		}
	}

	/** A plugin name answers to no directory, so the convention check compares it only to itself. */
	private void collectNameViolations(JsonNode manifest, List<String> violations) {
		String name = JsonNodes.textAt(manifest, NAME_KEY, null);
		if (name != null) {
			NameConvention.collect(name, name, pluginFile.toString(), violations);
		}
	}

	private void collectVersionViolation(JsonNode manifest, List<String> violations) {
		String version = JsonNodes.textAt(manifest, VERSION_KEY, null);
		if (version != null && !VERSION.matcher(version).matches()) {
			violations.add("plugin.json version '" + version + "' is not a dotted version number in: " + pluginFile);
		}
	}

	private void collectDescriptionViolation(JsonNode manifest, List<String> violations) {
		String description = JsonNodes.textAt(manifest, DESCRIPTION_KEY, null);
		if (description != null && description.isBlank()) {
			violations.add("plugin.json description must not be empty in: " + pluginFile);
		}
	}

	private List<String> requiredKeys() {
		return requiredKeys != null ? requiredKeys : DEFAULT_REQUIRED_KEYS;
	}

	void setPluginFile(File pluginFile) {
		this.pluginFile = pluginFile;
	}

	void setRequiredKeys(List<String> requiredKeys) {
		this.requiredKeys = requiredKeys;
	}

	void setAllowedKeys(List<String> allowedKeys) {
		this.allowedKeys = allowedKeys;
	}

	@Override
	public String toString() {
		return String.format("PluginFormatRule[pluginFile=%s]", pluginFile);
	}
}

package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;
import io.github.adamw7.tools.enforcer.rule.Violations;
import io.github.adamw7.tools.enforcer.text.NameConvention;

/**
 * Enforcer rule that validates a Claude Code plugin manifest, the
 * {@code .claude-plugin/plugin.json} of a plugin repository. The manifest names
 * the plugin in every marketplace listing, so a malformed one breaks installation
 * rather than the build that produced it.
 * <p>
 * When present, the file must be non-empty valid JSON declaring every required key
 * ({@code name} by default, overridable via {@code requiredKeys}). The
 * {@code name} is held to the Claude Code naming convention (lower-case
 * kebab-case, at most {@value NameConvention#MAX_LENGTH} characters), a
 * {@code version} must be a dotted number with optional pre-release and
 * build-metadata suffixes, and a {@code description} must be non-empty; each of the
 * three must be declared as a JSON string, and a key outside a configured
 * {@code allowedKeys} is reported, which catches typos such as {@code descripton}. Not every repository ships a plugin, so an absent
 * manifest is a pass.
 */
@Named("pluginFormat")
public class PluginFormatRule extends JsonFileRule {

	private static final String NAME_KEY = "name";
	private static final String VERSION_KEY = "version";
	private static final String DESCRIPTION_KEY = "description";
	private static final List<String> DEFAULT_REQUIRED_KEYS = List.of(NAME_KEY);

	/** A dotted version with the optional semver pre-release and build-metadata suffixes, e.g. {@code 1.0.0-beta.1+build.5}. */
	private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+){0,2}(-[A-Za-z0-9.-]+)?(\\+[A-Za-z0-9.-]+)?");

	/** The {@code .claude-plugin/plugin.json} manifest to validate. Injected from the rule configuration. */
	private File pluginFile;

	/** Optional override for the required manifest keys. */
	private List<String> requiredKeys;

	/** Optional whitelist of allowed manifest keys. When set, unknown keys are reported. */
	private List<String> allowedKeys;

	/** Not every repository ships a plugin, so an absent manifest is a pass. */
	public PluginFormatRule() {
		super("pluginFile", "plugin.json", OPTIONAL);
	}

	@Override
	protected File jsonFile() {
		return pluginFile;
	}

	@Override
	protected void collectViolations(JsonNode manifest, List<String> violations) {
		Violations.each(Objects.requireNonNullElse(requiredKeys, DEFAULT_REQUIRED_KEYS), key -> !manifest.has(key),
				key -> keyProblem("is missing required key '" + key + "'"), violations);
		Violations.eachDisallowed(JsonNodes.fieldNames(manifest), allowedKeys,
				key -> keyProblem("has unknown key '" + key + "'"), violations);
		collectNameViolations(manifest, violations);
		collectVersionViolation(manifest, violations);
		collectDescriptionViolation(manifest, violations);
	}

	private String keyProblem(String problem) {
		return "plugin.json " + problem + " in: " + pluginFile;
	}

	/**
	 * A plugin name answers to no directory, so the convention check compares it only
	 * to itself.
	 * <p>
	 * A value declared as anything but a string is reported as that, and not put
	 * through the convention check: a {@code "name": 123} read as its text satisfied
	 * kebab-case and shipped a manifest no marketplace can list, while a
	 * {@code "name": ["a"]} read as the empty string was reported as an empty name
	 * the author had plainly written something for.
	 */
	private void collectNameViolations(JsonNode manifest, List<String> violations) {
		if (collectNonTextViolation(manifest, NAME_KEY, violations)) {
			return;
		}
		String name = JsonNodes.textAt(manifest, NAME_KEY, null);
		if (name != null) {
			NameConvention.collect(name, name, pluginFile.toString(), violations);
		}
	}

	private void collectVersionViolation(JsonNode manifest, List<String> violations) {
		if (collectNonTextViolation(manifest, VERSION_KEY, violations)) {
			return;
		}
		String version = JsonNodes.textAt(manifest, VERSION_KEY, null);
		if (version != null && !VERSION.matcher(version).matches()) {
			violations.add("plugin.json version '" + version + "' is not a dotted version number in: " + pluginFile);
		}
	}

	private void collectDescriptionViolation(JsonNode manifest, List<String> violations) {
		if (collectNonTextViolation(manifest, DESCRIPTION_KEY, violations)) {
			return;
		}
		String description = JsonNodes.textAt(manifest, DESCRIPTION_KEY, null);
		if (description != null && description.isBlank()) {
			violations.add("plugin.json description must not be empty in: " + pluginFile);
		}
	}

	/** Reports a key declared as anything but a string, and says whether it did. */
	private boolean collectNonTextViolation(JsonNode manifest, String key, List<String> violations) {
		if (!JsonNodes.declaresNonText(manifest, key)) {
			return false;
		}
		violations.add(keyProblem("'" + key + "' must be a string"));
		return true;
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
}

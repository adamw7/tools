package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Base for enforcer rules that validate a single JSON configuration file: the file
 * parameter must be configured, the file must exist (unless the subclass treats
 * absence as a pass), be non-empty, and parse as JSON. A parse failure is collected
 * as a violation rather than thrown, so it is reported through the shared
 * {@link #report} path beside any structural problems.
 * <p>
 * A subclass names its file parameter and the description used in messages at
 * construction, and contributes the file, the report header and the checks against
 * the parsed {@link JsonNode}. A misconfigured rule or a missing or empty file
 * always fails as a build-setup mistake; a rule whose file is optional says so with
 * {@link #OPTIONAL} and passes on an absent one.
 */
public abstract class JsonFileRule extends ClaudeCodeEnforcerRule {

	/** Constructor flag for a rule whose file need not be there, so an absent one is a pass. */
	protected static final boolean OPTIONAL = true;

	private final String fileParameter;
	private final String description;
	private final boolean optional;

	/** A rule whose file must exist, the usual case. */
	protected JsonFileRule(String fileParameter, String description) {
		this(fileParameter, description, false);
	}

	/**
	 * @param fileParameter the configuration parameter name, used in the "not
	 *                      configured" message, e.g. {@code settingsFile}
	 * @param description   human-readable file name used in messages, e.g.
	 *                      {@code settings.json}
	 * @param optional      {@link #OPTIONAL} when an absent file is a pass
	 */
	protected JsonFileRule(String fileParameter, String description, boolean optional) {
		this.fileParameter = fileParameter;
		this.description = description;
		this.optional = optional;
	}

	@Override
	public final void execute() throws EnforcerRuleException {
		File file = jsonFile();
		requireConfigured(file, fileParameter);
		if (!file.isFile()) {
			reportAbsentFile(file);
			return;
		}
		List<String> violations = new ArrayList<>();
		JsonNode root = parse(file, violations).orElse(null);
		if (root != null) {
			collectViolations(root, violations);
		}
		report(header(), violations);
	}

	/**
	 * Fails on a required file and passes on an optional one. The pass still goes
	 * through {@link #report}, so a configured HTML report reflects this run rather
	 * than keeping a previous, failing one on disk.
	 */
	private void reportAbsentFile(File file) throws EnforcerRuleException {
		if (!optional) {
			requireExists(file, description);
		}
		log().debug(() -> description + " is absent at " + file + ", which this rule accepts; nothing to check");
		report(header(), List.of());
	}

	/**
	 * The parsed file, through {@link DocumentCache} so the four rules that each
	 * check a section of {@code settings.json} parse it once between them. A failed
	 * parse is not cached: the next rule to ask needs its violation too.
	 */
	private Optional<JsonNode> parse(File file, List<String> violations) throws EnforcerRuleException {
		String content = requireContent(file, description);
		return DocumentCache.parsed(file,
				() -> Optional.ofNullable(JsonNodes.parseObject(content, description, violations)));
	}

	/** The JSON file to validate. Injected from the rule configuration. */
	protected abstract File jsonFile();

	/**
	 * Document-specific checks against the parsed JSON. A subclass throws only for a
	 * build-setup mistake in its own configuration, such as a parameter that is not
	 * the regular expression it must be; a problem with the document itself belongs
	 * in {@code violations}.
	 */
	protected abstract void collectViolations(JsonNode root, List<String> violations) throws EnforcerRuleException;

	/**
	 * The optional object at {@code key}, for a rule validating one section of a
	 * larger file: empty when the file declares no such section (a pass), and empty
	 * with a violation when it declares one that is not an object, since a mistyped
	 * section must not slip through unvalidated.
	 */
	protected final Optional<JsonNode> section(JsonNode root, String key, List<String> violations) {
		if (!root.has(key)) {
			return Optional.empty();
		}
		JsonNode section = JsonNodes.objectAt(root, key);
		if (section == null) {
			violations.add(description + " '" + key + "' must be a JSON object");
			return Optional.empty();
		}
		return Optional.of(section);
	}

	/**
	 * The header that prefixes the grouped violation report. A rule that validates
	 * only part of its file overrides this to name that part, e.g.
	 * {@code settings.json permissions are not well formed:}.
	 */
	protected String header() {
		return description + " is not well formed:";
	}

	@Override
	protected List<String> howToFix() {
		return List.of(
				"Open " + description + " and confirm it parses as valid JSON.",
				"Correct every structural item listed above so it matches what the rule expects.",
				"Re-run the build to confirm " + description + " is well formed.");
	}
}

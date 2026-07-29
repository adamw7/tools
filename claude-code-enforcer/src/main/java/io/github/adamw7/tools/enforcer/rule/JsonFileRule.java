package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Base for enforcer rules that validate a single JSON configuration file. It owns
 * the scaffolding every such rule repeats: the file parameter must be configured,
 * the file must exist (unless the subclass treats absence as a pass), be non-empty,
 * and parse as JSON. A parse failure is collected as a violation rather than
 * thrown, so it is reported through the shared {@link #report} path alongside any
 * structural problems.
 * <p>
 * A subclass names its file parameter and the human-readable description used in
 * messages at construction, and contributes the file itself, the report header,
 * and the document-specific checks against the parsed {@link JsonNode}. A
 * misconfigured rule or a missing or empty file always fails, because that is a
 * build-setup mistake; a rule whose file is optional overrides
 * {@link #handleMissingFile} to pass instead.
 */
public abstract class JsonFileRule extends ClaudeCodeEnforcerRule {

	private final String fileParameter;
	private final String description;

	/**
	 * @param fileParameter the configuration parameter name, used in the "not
	 *                      configured" message, e.g. {@code settingsFile}
	 * @param description   human-readable file name used in messages, e.g.
	 *                      {@code settings.json}
	 */
	protected JsonFileRule(String fileParameter, String description) {
		this.fileParameter = fileParameter;
		this.description = description;
	}

	@Override
	public final void execute() throws EnforcerRuleException {
		File file = jsonFile();
		requireConfigured(file, fileParameter);
		if (!file.isFile()) {
			handleMissingFile(file);
			report(header(), List.of());
			return;
		}
		List<String> violations = new ArrayList<>();
		JsonNode root = JsonNodes.parseObject(requireContent(file, description), description, violations);
		if (root != null) {
			collectViolations(root, violations);
		}
		report(header(), violations);
	}

	/** The JSON file to validate. Injected from the rule configuration. */
	protected abstract File jsonFile();

	/** Document-specific checks against the parsed JSON. */
	protected abstract void collectViolations(JsonNode root, List<String> violations);

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

	/**
	 * How to react when the file is absent. The default fails the build, because a
	 * missing required file is a build-setup mistake. A rule whose file is optional
	 * overrides this to return, and the absent file is then reported as a pass — so
	 * a configured HTML report reflects that run rather than keeping the previous
	 * one's failure.
	 */
	protected void handleMissingFile(File file) throws EnforcerRuleException {
		requireExists(file, description);
	}
}

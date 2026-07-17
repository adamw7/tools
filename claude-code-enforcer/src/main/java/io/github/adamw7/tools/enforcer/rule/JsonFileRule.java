package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Base for enforcer rules that validate a single JSON configuration file. It
 * owns the scaffolding every such rule repeats: the file parameter must be
 * configured, the file must exist (unless the subclass treats absence as a
 * pass), it must be non-empty, and it must parse as JSON. A parse failure is
 * collected as a violation rather than thrown, so it is reported through the
 * shared {@link #report} path alongside any structural problems.
 * <p>
 * Subclasses contribute the file, its parameter name, a human-readable
 * description used in messages, the report header, and the document-specific
 * checks against the parsed {@link JsonNode}. A misconfigured rule (missing
 * file parameter) or a missing or empty file always fails, because that is a
 * build-setup mistake; a rule whose file is optional overrides
 * {@link #handleMissingFile} to pass instead.
 */
public abstract class JsonFileRule extends ClaudeCodeEnforcerRule {

	@Override
	public final void execute() throws EnforcerRuleException {
		File file = jsonFile();
		requireConfigured(file, fileParameter());
		if (!file.isFile()) {
			handleMissingFile(file);
			return;
		}
		List<String> violations = new ArrayList<>();
		JsonNode root = JsonNodes.parseObject(requireContent(file, description()), description(), violations);
		if (root != null) {
			collectViolations(root, violations);
		}
		report(header(), violations);
	}

	/** The JSON file to validate. Injected from the rule configuration. */
	protected abstract File jsonFile();

	/** The configuration parameter name, used in the "not configured" message, e.g. {@code settingsFile}. */
	protected abstract String fileParameter();

	/** Human-readable file name used in messages, e.g. {@code settings.json}. */
	protected abstract String description();

	/** The header that prefixes the grouped violation report. */
	protected abstract String header();

	/** Document-specific checks against the parsed JSON. */
	protected abstract void collectViolations(JsonNode root, List<String> violations);

	@Override
	protected List<String> howToFix() {
		return List.of(
				"Open " + description() + " and confirm it parses as valid JSON.",
				"Correct every structural item listed above so it matches what the rule expects.",
				"Re-run the build to confirm " + description() + " is well formed.");
	}

	/**
	 * How to react when the file is absent. The default fails the build, because a
	 * missing required file is a build-setup mistake. A rule whose file is optional
	 * overrides this to return without reporting.
	 */
	protected void handleMissingFile(File file) throws EnforcerRuleException {
		requireExists(file, description());
	}
}

package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.inject.Named;

import io.github.adamw7.tools.enforcer.rule.ProjectFiles;

/**
 * Enforcer rule that fails the build when any sub-agent definition under the
 * configured agents directory is malformed. Every {@code *.md} file directly in
 * {@code agentsDir} is treated as a sub-agent and must be non-empty, open with a
 * YAML front matter block declaring every required key, and carry a {@code name}
 * that follows the Claude Code naming convention and matches its file name.
 * <p>
 * The required keys default to {@code name} and {@code description}, and a
 * declared {@code description} must be non-empty, because Claude routes to a
 * sub-agent by matching intent against it. A declared {@code model} outside a
 * configured {@code allowedModels} is reported, so a typo such as
 * {@code claud-opus} cannot slip through. An agents directory with no definitions
 * is allowed; all problems found are reported together.
 */
@Named("subAgentFormat")
public class SubAgentFormatRule extends DefinitionFormatRule {

	private static final List<String> DEFAULT_REQUIRED_KEYS = List.of("name", "description");

	/** The {@code .claude/agents} directory to scan. Injected from the rule configuration. */
	private File agentsDir;

	/** Optional override for the required front matter keys. */
	private List<String> requiredKeys;

	/** Optional whitelist of model identifiers a sub-agent may declare. */
	private List<String> allowedModels;

	public SubAgentFormatRule() {
		super(new Naming("agentsDir", "Agents", "Sub-agent definition", "Sub-agent",
				"Sub-agent files are not well formed:"));
	}

	@Override
	protected File definitionDir() {
		return agentsDir;
	}

	@Override
	protected void collectEntryViolations(File definition, List<String> violations) {
		contentOf(definition, violations)
				.flatMap(content -> requiredFrontMatterOf(content, definition, violations))
				.ifPresent(checks -> collectFrontMatterViolations(definition, checks));
	}

	private void collectFrontMatterViolations(File definition, FrontMatterChecks checks) {
		checks.requireKeys(Objects.requireNonNullElse(requiredKeys, DEFAULT_REQUIRED_KEYS));
		checks.rejectDuplicateKeys();
		checks.checkName(ProjectFiles.markdownBaseName(definition));
		checks.checkDescription(0);
		checks.checkModel(allowedModels);
	}

	void setAgentsDir(File agentsDir) {
		this.agentsDir = agentsDir;
	}

	void setRequiredKeys(List<String> requiredKeys) {
		this.requiredKeys = requiredKeys;
	}

	void setAllowedModels(List<String> allowedModels) {
		this.allowedModels = allowedModels;
	}
}

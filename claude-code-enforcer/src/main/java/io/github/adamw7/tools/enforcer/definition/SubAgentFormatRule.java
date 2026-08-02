package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.FrontMatter;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

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
public class SubAgentFormatRule extends ClaudeCodeEnforcerRule {

	private static final String LABEL = "Sub-agent";
	private static final String DEFINITION = "sub-agent definition";
	private static final List<String> DEFAULT_REQUIRED_KEYS = List.of("name", "description");

	/** The {@code .claude/agents} directory to scan. Injected from the rule configuration. */
	private File agentsDir;

	/** Optional override for the required front matter keys. */
	private List<String> requiredKeys;

	/** Optional whitelist of model identifiers a sub-agent may declare. */
	private List<String> allowedModels;

	/** When true, a malformed front matter block is rewritten in place instead of failing the build. */
	private boolean autoFix;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(agentsDir, "agentsDir");
		DefinitionFiles.verifyDirectory(agentsDir, "Agents");
		List<String> violations = new ArrayList<>();
		for (File definition : DefinitionFiles.markdownFiles(agentsDir)) {
			collectDefinitionViolations(definition, violations);
		}
		report("Sub-agent files are not well formed:", violations);
	}

	/**
	 * A definition that cannot be decoded as text is reported rather than read. The
	 * rule scans a directory whose contents it does not control, and an
	 * {@link java.io.UncheckedIOException} escaping here would abort the build as an
	 * internal error instead of reporting the malformed definition it exists to
	 * catch.
	 */
	private void collectDefinitionViolations(File definition, List<String> violations) {
		Optional<String> text = MarkdownText.readIfText(definition);
		if (text.isEmpty()) {
			violations.add("Sub-agent definition cannot be read as text: " + definition);
			return;
		}
		String content = text.get();
		if (content.isBlank()) {
			violations.add("Sub-agent definition is empty: " + definition);
			return;
		}
		String fixed = FrontMatterAutoFix.apply(definition, DEFINITION, content, autoFix, getLog());
		Optional<FrontMatter> frontMatter = FrontMatter.parse(fixed);
		if (frontMatter.isEmpty()) {
			violations.add("Sub-agent definition must start with a YAML front matter block delimited by '---': "
					+ definition);
		} else {
			collectFrontMatterViolations(definition, frontMatter.get(), violations);
		}
	}

	private void collectFrontMatterViolations(File definition, FrontMatter frontMatter, List<String> violations) {
		FrontMatterChecks checks = new FrontMatterChecks(frontMatter, LABEL, definition, violations);
		checks.requireKeys(Objects.requireNonNullElse(requiredKeys, DEFAULT_REQUIRED_KEYS));
		checks.checkName(DefinitionFiles.baseName(definition));
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

	void setAutoFix(boolean autoFix) {
		this.autoFix = autoFix;
	}
}

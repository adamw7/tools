package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.FrontMatter;
import io.github.adamw7.tools.enforcer.text.MarkdownText;
import io.github.adamw7.tools.enforcer.text.NameConvention;

/**
 * Enforcer rule that fails the build when any sub-agent definition under the
 * configured agents directory is malformed. Every {@code *.md} file directly in
 * {@code agentsDir} is treated as a sub-agent and must be non-empty, open with a
 * YAML front matter block declaring every required key, and carry a {@code name}
 * that follows the Claude Code naming convention and matches its file name.
 * <p>
 * The required keys default to {@code name} and {@code description}. When
 * {@code allowedModels} is configured and a definition declares a {@code model},
 * that model must be one of the allowed values, so a typo such as
 * {@code claud-opus} cannot slip through. An agents directory with no
 * definitions is allowed; all problems found are reported together.
 */
@Named("subAgentFormat")
public class SubAgentFormatRule extends ClaudeCodeEnforcerRule {

	private static final String MARKDOWN_SUFFIX = ".md";
	private static final String NAME_KEY = "name";
	private static final String DESCRIPTION_KEY = "description";
	private static final String MODEL_KEY = "model";
	private static final List<String> DEFAULT_REQUIRED_KEYS = List.of(NAME_KEY, DESCRIPTION_KEY);

	/** The {@code .claude/agents} directory to scan. Injected from the rule configuration. */
	private File agentsDir;

	/** Optional override for the required front matter keys. */
	private List<String> requiredKeys;

	/** Optional whitelist of model identifiers a sub-agent may declare. */
	private List<String> allowedModels;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		verifyDirectory();
		List<String> violations = new ArrayList<>();
		for (File definition : listDefinitions()) {
			collectDefinitionViolations(definition, violations);
		}
		report("Sub-agent files are not well formed:", violations);
	}

	private void verifyConfigured() throws EnforcerRuleException {
		if (agentsDir == null) {
			throw new EnforcerRuleException("The agentsDir parameter is not configured");
		}
	}

	private void verifyDirectory() throws EnforcerRuleException {
		if (!agentsDir.isDirectory()) {
			throw new EnforcerRuleException("Agents directory does not exist at " + agentsDir);
		}
	}

	private File[] listDefinitions() {
		File[] definitions = agentsDir.listFiles(this::isMarkdownFile);
		return definitions != null ? definitions : new File[0];
	}

	private boolean isMarkdownFile(File file) {
		return file.isFile() && file.getName().endsWith(MARKDOWN_SUFFIX);
	}

	private void collectDefinitionViolations(File definition, List<String> violations) {
		String content = readContent(definition);
		if (content.isBlank()) {
			violations.add("Sub-agent definition is empty: " + definition);
		} else {
			collectFrontMatterViolations(definition, content, violations);
		}
	}

	private String readContent(File definition) {
		try {
			return MarkdownText.stripByteOrderMark(Files.readString(definition.toPath()));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read sub-agent definition at " + definition, e);
		}
	}

	private void collectFrontMatterViolations(File definition, String content, List<String> violations) {
		Optional<FrontMatter> frontMatter = FrontMatter.parse(content);
		if (frontMatter.isEmpty()) {
			violations.add("Sub-agent definition must start with a YAML front matter block delimited by '---': "
					+ definition);
		} else {
			collectFrontMatterViolations(definition, frontMatter.get(), violations);
		}
	}

	private void collectFrontMatterViolations(File definition, FrontMatter frontMatter, List<String> violations) {
		collectMissingKeys(definition, frontMatter, violations);
		collectNameViolations(definition, frontMatter, violations);
		collectModelViolations(definition, frontMatter, violations);
	}

	private void collectMissingKeys(File definition, FrontMatter frontMatter, List<String> violations) {
		for (String key : requiredKeys()) {
			if (!frontMatter.hasKey(key)) {
				violations.add("Sub-agent front matter is missing '" + key + ":' in: " + definition);
			}
		}
	}

	private void collectNameViolations(File definition, FrontMatter frontMatter, List<String> violations) {
		frontMatter.value(NAME_KEY).ifPresent(
				name -> NameConvention.collect(name, baseName(definition), definition.toString(), violations));
	}

	private void collectModelViolations(File definition, FrontMatter frontMatter, List<String> violations) {
		if (allowedModels == null) {
			return;
		}
		frontMatter.value(MODEL_KEY).ifPresent(model -> addModelViolation(definition, model, violations));
	}

	private void addModelViolation(File definition, String model, List<String> violations) {
		if (!allowedModels.contains(model)) {
			violations.add("Sub-agent declares unsupported model '" + model + "' in: " + definition);
		}
	}

	private String baseName(File definition) {
		String name = definition.getName();
		return name.substring(0, name.length() - MARKDOWN_SUFFIX.length());
	}

	private List<String> requiredKeys() {
		return requiredKeys != null ? requiredKeys : DEFAULT_REQUIRED_KEYS;
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

	@Override
	public String toString() {
		return String.format("SubAgentFormatRule[agentsDir=%s]", agentsDir);
	}
}

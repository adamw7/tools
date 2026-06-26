package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.FrontMatter;
import io.github.adamw7.tools.enforcer.text.MarkdownText;
import io.github.adamw7.tools.enforcer.text.NameConvention;

/**
 * Enforcer rule that fails the build when any custom slash command under the
 * configured commands directory is malformed. Every {@code *.md} file directly
 * in {@code commandsDir} is treated as a slash command: it must be non-empty and
 * carry a file name that follows the Claude Code naming convention, because the
 * command's name is taken from its file name rather than from front matter.
 * <p>
 * Front matter is optional for a command. When a command does open with a YAML
 * front matter block, the optional checks apply: a present {@code description}
 * must be non-empty; a present {@code model} must be one of {@code allowedModels}
 * when that whitelist is configured; and when {@code allowedFrontMatterKeys} is
 * configured, any key outside that set is reported, which catches typos such as
 * {@code argument-hnt}.
 * <p>
 * A commands directory with no commands is allowed; all problems found are
 * reported together.
 */
@Named("commandFormat")
public class CommandFormatRule extends ClaudeCodeEnforcerRule {

	private static final String DESCRIPTION_KEY = "description";
	private static final String MODEL_KEY = "model";

	/** The {@code .claude/commands} directory to scan. Injected from the rule configuration. */
	private File commandsDir;

	/** Optional whitelist of allowed front matter keys. When set, unknown keys are reported. */
	private List<String> allowedFrontMatterKeys;

	/** Optional whitelist of model identifiers a command may declare. */
	private List<String> allowedModels;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		DefinitionFiles.verifyDirectory(commandsDir, "Commands");
		List<String> violations = new ArrayList<>();
		for (File command : DefinitionFiles.markdownFiles(commandsDir)) {
			collectCommandViolations(command, violations);
		}
		report("Command files are not well formed:", violations);
	}

	private void verifyConfigured() throws EnforcerRuleException {
		if (commandsDir == null) {
			throw new EnforcerRuleException("The commandsDir parameter is not configured");
		}
	}

	private void collectCommandViolations(File command, List<String> violations) {
		String content = MarkdownText.read(command, "command definition");
		if (content.isBlank()) {
			violations.add("Command definition is empty: " + command);
		} else {
			collectNonEmptyViolations(command, content, violations);
		}
	}

	private void collectNonEmptyViolations(File command, String content, List<String> violations) {
		String baseName = DefinitionFiles.baseName(command);
		NameConvention.collect(baseName, baseName, command.toString(), violations);
		FrontMatter.parse(content)
				.ifPresent(frontMatter -> collectFrontMatterViolations(command, frontMatter, violations));
	}

	private void collectFrontMatterViolations(File command, FrontMatter frontMatter, List<String> violations) {
		collectUnknownKeys(command, frontMatter, violations);
		collectDescriptionViolations(command, frontMatter, violations);
		collectModelViolations(command, frontMatter, violations);
	}

	private void collectUnknownKeys(File command, FrontMatter frontMatter, List<String> violations) {
		if (allowedFrontMatterKeys == null) {
			return;
		}
		for (String key : frontMatter.keys()) {
			addUnknownKeyViolation(command, key, violations);
		}
	}

	private void addUnknownKeyViolation(File command, String key, List<String> violations) {
		if (!allowedFrontMatterKeys.contains(key)) {
			violations.add("Command front matter has unknown key '" + key + ":' in: " + command);
		}
	}

	private void collectDescriptionViolations(File command, FrontMatter frontMatter, List<String> violations) {
		frontMatter.value(DESCRIPTION_KEY).ifPresent(description -> addDescriptionViolation(command, description, violations));
	}

	private void addDescriptionViolation(File command, String description, List<String> violations) {
		if (description.isBlank()) {
			violations.add("Command description must not be empty in: " + command);
		}
	}

	private void collectModelViolations(File command, FrontMatter frontMatter, List<String> violations) {
		if (allowedModels == null) {
			return;
		}
		frontMatter.value(MODEL_KEY).ifPresent(model -> addModelViolation(command, model, violations));
	}

	private void addModelViolation(File command, String model, List<String> violations) {
		if (!allowedModels.contains(model)) {
			violations.add("Command declares unsupported model '" + model + "' in: " + command);
		}
	}

	void setCommandsDir(File commandsDir) {
		this.commandsDir = commandsDir;
	}

	void setAllowedFrontMatterKeys(List<String> allowedFrontMatterKeys) {
		this.allowedFrontMatterKeys = allowedFrontMatterKeys;
	}

	void setAllowedModels(List<String> allowedModels) {
		this.allowedModels = allowedModels;
	}

	@Override
	public String toString() {
		return String.format("CommandFormatRule[commandsDir=%s]", commandsDir);
	}
}

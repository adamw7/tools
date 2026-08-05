package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.FrontMatter;
import io.github.adamw7.tools.enforcer.text.NameConvention;

/**
 * Enforcer rule that fails the build when any custom slash command under the
 * configured commands directory is malformed. Every {@code *.md} file directly in
 * {@code commandsDir} is treated as a slash command: it must be non-empty and
 * carry a file name that follows the Claude Code naming convention, because the
 * command's name is taken from its file name rather than from front matter.
 * <p>
 * Front matter is optional for a command. When one is present, a declared
 * {@code description} must be non-empty, a declared {@code model} must be one of
 * {@code allowedModels} when that whitelist is configured, and any key outside a
 * configured {@code allowedFrontMatterKeys} is reported, which catches typos such
 * as {@code argument-hnt}. A commands directory with no commands is allowed; all
 * problems found are reported together.
 */
@Named("commandFormat")
public class CommandFormatRule extends ClaudeCodeEnforcerRule {

	private static final String LABEL = "Command";
	private static final String DEFINITION = "Command definition";

	/** The {@code .claude/commands} directory to scan. Injected from the rule configuration. */
	private File commandsDir;

	/** Optional whitelist of allowed front matter keys. When set, unknown keys are reported. */
	private List<String> allowedFrontMatterKeys;

	/** Optional whitelist of model identifiers a command may declare. */
	private List<String> allowedModels;

	/** When true, a malformed front matter block is rewritten in place instead of failing the build. */
	private boolean autoFix;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(commandsDir, "commandsDir");
		DefinitionFiles.verifyDirectory(commandsDir, "Commands");
		List<String> violations = new ArrayList<>();
		for (File command : DefinitionFiles.markdownFiles(commandsDir)) {
			collectCommandViolations(command, violations);
		}
		report("Command files are not well formed:", violations);
	}

	private void collectCommandViolations(File command, List<String> violations) {
		DefinitionContent.of(command, DEFINITION, autoFix, getLog(), violations)
				.ifPresent(content -> collectNamedViolations(command, content, violations));
	}

	private void collectNamedViolations(File command, String content, List<String> violations) {
		String baseName = DefinitionFiles.baseName(command);
		NameConvention.collect(baseName, baseName, command.toString(), violations);
		FrontMatter.parse(content)
				.ifPresent(frontMatter -> collectFrontMatterViolations(command, frontMatter, violations));
	}

	private void collectFrontMatterViolations(File command, FrontMatter frontMatter, List<String> violations) {
		FrontMatterChecks checks = new FrontMatterChecks(frontMatter, LABEL, command, violations);
		checks.rejectDuplicateKeys();
		checks.allowOnlyKeys(allowedFrontMatterKeys);
		checks.checkDescription(0);
		checks.checkModel(allowedModels);
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

	void setAutoFix(boolean autoFix) {
		this.autoFix = autoFix;
	}
}

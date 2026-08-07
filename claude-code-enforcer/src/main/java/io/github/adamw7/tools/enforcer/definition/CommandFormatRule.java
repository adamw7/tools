package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.List;

import javax.inject.Named;

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
public class CommandFormatRule extends DefinitionFormatRule {

	/** The {@code .claude/commands} directory to scan. Injected from the rule configuration. */
	private File commandsDir;

	/** Optional whitelist of allowed front matter keys. When set, unknown keys are reported. */
	private List<String> allowedFrontMatterKeys;

	/** Optional whitelist of model identifiers a command may declare. */
	private List<String> allowedModels;

	public CommandFormatRule() {
		super(new Naming("commandsDir", "Commands", "Command definition", "Command",
				"Command files are not well formed:"));
	}

	@Override
	protected File definitionDir() {
		return commandsDir;
	}

	@Override
	protected void collectEntryViolations(File command, List<String> violations) {
		contentOf(command, violations).ifPresent(content -> collectNamedViolations(command, content, violations));
	}

	/** A command answers to its file name, so the convention check compares that name only to itself. */
	private void collectNamedViolations(File command, String content, List<String> violations) {
		String baseName = DefinitionFiles.baseName(command);
		NameConvention.collect(baseName, baseName, command.toString(), violations);
		frontMatterOf(content, command, violations).ifPresent(this::collectFrontMatterViolations);
	}

	private void collectFrontMatterViolations(FrontMatterChecks checks) {
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
}

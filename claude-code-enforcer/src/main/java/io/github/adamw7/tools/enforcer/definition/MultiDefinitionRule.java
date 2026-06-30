package io.github.adamw7.tools.enforcer.definition;

import java.io.File;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * Base for the enforcer rules that check a property across <em>every</em> Claude
 * Code definition at once (currently uniqueness of names and of descriptions).
 * They all scan the same three optional directories - {@code commandsDir},
 * {@code agentsDir} and {@code skillsDir} - where a command and a sub-agent are
 * each a {@code *.md} file and a skill is a directory containing a
 * {@code SKILL.md}. This class owns that shared configuration and traversal so a
 * subclass only describes what it does with each definition.
 * <p>
 * The three directories are independent: each is optional and at least one must
 * be configured. A directory that is configured must exist, because that is a
 * build-setup mistake rather than a content problem.
 */
abstract class MultiDefinitionRule extends ClaudeCodeEnforcerRule {

	private static final String SKILL_FILE_NAME = "SKILL.md";

	/** The {@code .claude/commands} directory to scan. Injected from the rule configuration. */
	private File commandsDir;

	/** The {@code .claude/agents} directory to scan. Injected from the rule configuration. */
	private File agentsDir;

	/** The {@code .claude/skills} directory to scan. Injected from the rule configuration. */
	private File skillsDir;

	/** Receives each definition discovered across the configured directories. */
	@FunctionalInterface
	interface DefinitionVisitor {
		/**
		 * @param definitionFile the Markdown file carrying the definition (the
		 *                        {@code *.md} for commands and sub-agents, the
		 *                        {@code SKILL.md} for skills)
		 * @param source          the file or directory that names the definition
		 * @param name            the definition's name (file base name, or skill
		 *                        directory name)
		 */
		void visit(File definitionFile, File source, String name);
	}

	protected final void verifyConfigured() throws EnforcerRuleException {
		if (commandsDir == null && agentsDir == null && skillsDir == null) {
			throw new EnforcerRuleException(
					"At least one of commandsDir, agentsDir or skillsDir must be configured");
		}
	}

	/**
	 * Walks every configured directory and hands each definition to {@code visitor}.
	 * Commands and sub-agents are the {@code *.md} files in their directories; skills
	 * are the immediate subdirectories of {@code skillsDir}.
	 */
	protected final void forEachDefinition(DefinitionVisitor visitor) throws EnforcerRuleException {
		visitMarkdown(commandsDir, "Commands", visitor);
		visitMarkdown(agentsDir, "Agents", visitor);
		visitSkills(skillsDir, visitor);
	}

	private void visitMarkdown(File directory, String label, DefinitionVisitor visitor)
			throws EnforcerRuleException {
		if (directory == null) {
			return;
		}
		DefinitionFiles.verifyDirectory(directory, label);
		for (File markdown : DefinitionFiles.markdownFiles(directory)) {
			visitor.visit(markdown, markdown, DefinitionFiles.baseName(markdown));
		}
	}

	private void visitSkills(File directory, DefinitionVisitor visitor) throws EnforcerRuleException {
		if (directory == null) {
			return;
		}
		DefinitionFiles.verifyDirectory(directory, "Skills");
		for (File skill : DefinitionFiles.subdirectories(directory)) {
			visitor.visit(new File(skill, SKILL_FILE_NAME), skill, skill.getName());
		}
	}

	void setCommandsDir(File commandsDir) {
		this.commandsDir = commandsDir;
	}

	void setAgentsDir(File agentsDir) {
		this.agentsDir = agentsDir;
	}

	void setSkillsDir(File skillsDir) {
		this.skillsDir = skillsDir;
	}

	@Override
	public String toString() {
		return String.format("%s[commandsDir=%s, agentsDir=%s, skillsDir=%s]",
				getClass().getSimpleName(), commandsDir, agentsDir, skillsDir);
	}
}

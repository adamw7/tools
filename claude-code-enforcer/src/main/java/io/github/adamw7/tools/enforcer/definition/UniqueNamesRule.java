package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * Enforcer rule that fails the build when two Claude Code definitions claim the
 * same name. A command's name is its {@code *.md} file name, a sub-agent's name
 * is its {@code *.md} file name, and a skill's name is its directory name, so a
 * command and a sub-agent both called {@code review}, or two skills called
 * {@code commit}, are a real source of confusion. The rule gathers the names
 * from every configured directory and reports each name that is used more than
 * once, naming every file or directory that uses it.
 * <p>
 * The three directories are independent: {@code commandsDir}, {@code agentsDir}
 * and {@code skillsDir} are each optional, and at least one must be configured.
 * A directory that is configured must exist, because that is a build-setup
 * mistake rather than a naming problem. Uniqueness is checked across every
 * configured directory at once, so a clash between a command and a skill is
 * caught just like a clash between two commands. All clashes found are reported
 * together.
 */
@Named("uniqueNames")
public class UniqueNamesRule extends ClaudeCodeEnforcerRule {

	/** The {@code .claude/commands} directory to scan. Injected from the rule configuration. */
	private File commandsDir;

	/** The {@code .claude/agents} directory to scan. Injected from the rule configuration. */
	private File agentsDir;

	/** The {@code .claude/skills} directory to scan. Injected from the rule configuration. */
	private File skillsDir;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		Map<String, List<String>> sourcesByName = new LinkedHashMap<>();
		collectMarkdownNames(commandsDir, "Commands", sourcesByName);
		collectMarkdownNames(agentsDir, "Agents", sourcesByName);
		collectSkillNames(skillsDir, sourcesByName);
		report("Claude Code names must be unique:", duplicates(sourcesByName));
	}

	private void verifyConfigured() throws EnforcerRuleException {
		if (commandsDir == null && agentsDir == null && skillsDir == null) {
			throw new EnforcerRuleException(
					"At least one of commandsDir, agentsDir or skillsDir must be configured");
		}
	}

	private void collectMarkdownNames(File directory, String label, Map<String, List<String>> sourcesByName)
			throws EnforcerRuleException {
		if (directory == null) {
			return;
		}
		DefinitionFiles.verifyDirectory(directory, label);
		for (File markdown : DefinitionFiles.markdownFiles(directory)) {
			record(DefinitionFiles.baseName(markdown), markdown.toString(), sourcesByName);
		}
	}

	private void collectSkillNames(File directory, Map<String, List<String>> sourcesByName)
			throws EnforcerRuleException {
		if (directory == null) {
			return;
		}
		DefinitionFiles.verifyDirectory(directory, "Skills");
		for (File skill : DefinitionFiles.subdirectories(directory)) {
			record(skill.getName(), skill.toString(), sourcesByName);
		}
	}

	private void record(String name, String source, Map<String, List<String>> sourcesByName) {
		sourcesByName.computeIfAbsent(name, key -> new ArrayList<>()).add(source);
	}

	private List<String> duplicates(Map<String, List<String>> sourcesByName) {
		List<String> violations = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : sourcesByName.entrySet()) {
			addDuplicateViolation(entry, violations);
		}
		return violations;
	}

	private void addDuplicateViolation(Map.Entry<String, List<String>> entry, List<String> violations) {
		List<String> sources = entry.getValue();
		if (sources.size() > 1) {
			violations.add("name '" + entry.getKey() + "' is used by " + sources.size()
					+ " definitions: " + String.join(", ", sources));
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
		return String.format("UniqueNamesRule[commandsDir=%s, agentsDir=%s, skillsDir=%s]",
				commandsDir, agentsDir, skillsDir);
	}
}

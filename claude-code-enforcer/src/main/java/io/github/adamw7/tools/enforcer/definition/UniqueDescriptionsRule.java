package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.FrontMatter;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that fails the build when two Claude Code definitions share the
 * same {@code description}. Claude routes to a skill, sub-agent, or command by
 * matching the user's intent against these descriptions, so two definitions that
 * describe themselves identically are ambiguous and one will shadow the other.
 * The rule reads the {@code description} from the front matter of every
 * sub-agent ({@code *.md}), command ({@code *.md}), and skill ({@code SKILL.md})
 * in the configured directories and reports each description used more than once,
 * naming every file that uses it.
 * <p>
 * Comparison ignores case and runs of whitespace, so {@code Reviews code.} and
 * {@code reviews   code.} are treated as the same description. Definitions with
 * no description, or a blank one, are skipped here because the format rules
 * already report those. The three directories ({@code commandsDir},
 * {@code agentsDir}, {@code skillsDir}) are each optional and at least one must
 * be configured; a configured directory must exist. All clashes are reported
 * together.
 */
@Named("uniqueDescriptions")
public class UniqueDescriptionsRule extends ClaudeCodeEnforcerRule {

	private static final String DESCRIPTION_KEY = "description";
	private static final String SKILL_FILE_NAME = "SKILL.md";

	/** The {@code .claude/commands} directory to scan. Injected from the rule configuration. */
	private File commandsDir;

	/** The {@code .claude/agents} directory to scan. Injected from the rule configuration. */
	private File agentsDir;

	/** The {@code .claude/skills} directory to scan. Injected from the rule configuration. */
	private File skillsDir;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		Map<String, Description> byNormalizedText = new LinkedHashMap<>();
		collectMarkdownDescriptions(commandsDir, "Commands", byNormalizedText);
		collectMarkdownDescriptions(agentsDir, "Agents", byNormalizedText);
		collectSkillDescriptions(skillsDir, byNormalizedText);
		report("Claude Code descriptions must be unique:", duplicates(byNormalizedText));
	}

	private void verifyConfigured() throws EnforcerRuleException {
		if (commandsDir == null && agentsDir == null && skillsDir == null) {
			throw new EnforcerRuleException(
					"At least one of commandsDir, agentsDir or skillsDir must be configured");
		}
	}

	private void collectMarkdownDescriptions(File directory, String label, Map<String, Description> byText)
			throws EnforcerRuleException {
		if (directory == null) {
			return;
		}
		DefinitionFiles.verifyDirectory(directory, label);
		for (File markdown : DefinitionFiles.markdownFiles(directory)) {
			record(markdown, markdown, byText);
		}
	}

	private void collectSkillDescriptions(File directory, Map<String, Description> byText)
			throws EnforcerRuleException {
		if (directory == null) {
			return;
		}
		DefinitionFiles.verifyDirectory(directory, "Skills");
		for (File skill : DefinitionFiles.subdirectories(directory)) {
			record(new File(skill, SKILL_FILE_NAME), skill, byText);
		}
	}

	private void record(File definitionFile, File source, Map<String, Description> byText) {
		descriptionOf(definitionFile).ifPresent(text -> add(text, source, byText));
	}

	private Optional<String> descriptionOf(File definitionFile) {
		if (!definitionFile.isFile()) {
			return Optional.empty();
		}
		String content = MarkdownText.read(definitionFile, "definition");
		return FrontMatter.parse(content)
				.flatMap(frontMatter -> frontMatter.value(DESCRIPTION_KEY))
				.filter(value -> !value.isBlank());
	}

	private void add(String text, File source, Map<String, Description> byText) {
		byText.computeIfAbsent(normalize(text), key -> new Description(text)).addSource(source.toString());
	}

	private String normalize(String text) {
		return text.strip().toLowerCase().replaceAll("\\s+", " ");
	}

	private List<String> duplicates(Map<String, Description> byText) {
		List<String> violations = new ArrayList<>();
		for (Description description : byText.values()) {
			description.addDuplicateViolation(violations);
		}
		return violations;
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
		return String.format("UniqueDescriptionsRule[commandsDir=%s, agentsDir=%s, skillsDir=%s]",
				commandsDir, agentsDir, skillsDir);
	}

	/** A description's original text and the sources that declare an equivalent of it. */
	private static final class Description {

		private final String text;
		private final List<String> sources = new ArrayList<>();

		private Description(String text) {
			this.text = text;
		}

		private void addSource(String source) {
			sources.add(source);
		}

		private void addDuplicateViolation(List<String> violations) {
			if (sources.size() > 1) {
				violations.add("description '" + text + "' is used by " + sources.size()
						+ " definitions: " + String.join(", ", sources));
			}
		}
	}
}

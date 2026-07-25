package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.Optional;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

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
 * already report those. All clashes are reported together.
 */
@Named("uniqueDescriptions")
public class UniqueDescriptionsRule extends MultiDefinitionRule {

	private static final String DESCRIPTION_KEY = "description";

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		Duplicates descriptions = new Duplicates();
		forEachDefinition((definitionFile, source, name) -> record(definitionFile, source, descriptions));
		report("Claude Code descriptions must be unique:", descriptions.violations("description"));
	}

	private void record(File definitionFile, File source, Duplicates descriptions) {
		descriptionOf(definitionFile)
				.ifPresent(text -> descriptions.add(normalize(text), text, source.toString()));
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

	private String normalize(String text) {
		return text.strip().toLowerCase().replaceAll("\\s+", " ");
	}
}

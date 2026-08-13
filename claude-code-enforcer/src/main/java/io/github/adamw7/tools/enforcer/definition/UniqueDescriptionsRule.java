package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.Locale;
import java.util.Optional;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.text.FrontMatter;
import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * Enforcer rule that fails the build when two Claude Code definitions share the
 * same {@code description}. Claude routes to a skill, sub-agent, or command by
 * matching the user's intent against these descriptions, so two definitions that
 * describe themselves identically are ambiguous and one will shadow the other. The
 * rule reads the {@code description} from the front matter of every sub-agent,
 * command, and skill in the configured directories and reports each description
 * used more than once, naming every file that uses it.
 * <p>
 * Comparison ignores case and runs of whitespace, so {@code Reviews code.} and
 * {@code reviews   code.} are the same description. Definitions with no
 * description, or a blank one, are skipped here because the format rules already
 * report those.
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

	/**
	 * A definition that cannot be decoded as text declares no description here. The
	 * format rules report the unreadable file; an
	 * {@link java.io.UncheckedIOException} escaping this rule would abort the build
	 * as an internal error instead.
	 */
	private Optional<String> descriptionOf(File definitionFile) {
		if (!definitionFile.isFile()) {
			return Optional.empty();
		}
		return MarkdownText.readIfText(definitionFile)
				.flatMap(FrontMatter::parse)
				.flatMap(frontMatter -> frontMatter.value(DESCRIPTION_KEY))
				.filter(value -> !value.isBlank());
	}

	/**
	 * Case is folded in the root locale rather than the machine's, so which
	 * descriptions count as duplicates is a property of the definitions and not of
	 * where the build runs. The default locale folds {@code I} to a dotless
	 * {@code ı} in Turkish, which made two descriptions collide on one developer's
	 * machine and not on another's — the least reproducible way for a rule to fail.
	 */
	private String normalize(String text) {
		return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}
}

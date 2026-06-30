package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
		Map<String, Description> byNormalizedText = new LinkedHashMap<>();
		forEachDefinition((definitionFile, source, name) -> record(definitionFile, source, byNormalizedText));
		report("Claude Code descriptions must be unique:", duplicates(byNormalizedText));
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

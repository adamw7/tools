package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.List;
import java.util.Optional;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;

import io.github.adamw7.tools.enforcer.text.FrontMatterFixer;
import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * The text of one Claude Code definition, ready to be validated: read, checked for
 * emptiness, and auto-fixed. Every definition rule starts the same way, so the
 * three preliminaries live here and each rule keeps only its own checks.
 * <p>
 * A file that cannot be decoded as text is reported rather than read. These rules
 * scan a directory whose contents they do not control, and an
 * {@link java.io.UncheckedIOException} escaping one would abort the build as an
 * internal error instead of reporting the malformed definition it exists to catch.
 */
final class DefinitionContent {

	private DefinitionContent() {
	}

	/**
	 * @param label the definition kind as messages name it, e.g. {@code SKILL.md} or
	 *              {@code Sub-agent definition}
	 * @return the content to validate, or empty when a violation was collected
	 *         instead
	 */
	static Optional<String> of(File file, String label, boolean autoFix, EnforcerLogger log,
			List<String> violations) {
		Optional<String> text = MarkdownText.readIfText(file);
		if (text.isEmpty()) {
			violations.add(label + " cannot be read as text: " + file);
			return Optional.empty();
		}
		if (text.get().isBlank()) {
			violations.add(label + " is empty: " + file);
			return Optional.empty();
		}
		return Optional.of(autoFixed(file, label, text.get(), autoFix, log));
	}

	/**
	 * Repairs a malformed front matter block when auto-fix is on, rewriting the file
	 * and answering the repaired content so the rest of the checks run against the
	 * corrected document instead of failing the build. Returns {@code content}
	 * unchanged when auto-fix is off or there is nothing to repair.
	 */
	private static String autoFixed(File file, String label, String content, boolean autoFix, EnforcerLogger log) {
		if (!autoFix) {
			return content;
		}
		Optional<String> repaired = FrontMatterFixer.repair(content);
		if (repaired.isEmpty()) {
			return content;
		}
		MarkdownText.write(file, repaired.get(), label);
		log.info("Auto-fixed malformed front matter in " + file);
		return repaired.get();
	}
}

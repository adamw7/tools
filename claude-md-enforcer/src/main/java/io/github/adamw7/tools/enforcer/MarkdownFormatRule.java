package io.github.adamw7.tools.enforcer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Base for enforcer rules that validate a Markdown document follows an expected
 * structure: it must exist, be non-empty, start with a required title heading
 * (a leading UTF-8 BOM is tolerated), and contain every required section
 * heading. Subclasses contribute the file, its name, the title, the required
 * sections, and any document-specific checks.
 */
abstract class MarkdownFormatRule extends AbstractEnforcerRule {

	private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

	@Override
	public void execute() throws EnforcerRuleException {
		String content = readContent();
		verifyTitle(content);
		verifyRequiredSections(content);
		verifyAdditional(content);
	}

	/** The file to validate. Injected from the rule configuration. */
	protected abstract File documentFile();

	/** Human-readable file name used in messages, e.g. {@code CLAUDE.md}. */
	protected abstract String documentName();

	/** The title heading the document must start with, e.g. {@code # CLAUDE.md}. */
	protected abstract String titleHeading();

	/** The section headings the document must contain. */
	protected abstract List<String> requiredSections();

	/** Hook for document-specific checks. The default implementation does nothing. */
	protected void verifyAdditional(String content) throws EnforcerRuleException {
	}

	private String readContent() throws EnforcerRuleException {
		File file = documentFile();
		if (file == null) {
			throw new EnforcerRuleException("The " + documentName() + " file parameter is not configured");
		}
		if (!file.isFile()) {
			throw new EnforcerRuleException(documentName() + " does not exist at " + file);
		}
		String content = stripByteOrderMark(readAll(file));
		if (content.isBlank()) {
			throw new EnforcerRuleException(documentName() + " is empty: " + file);
		}
		return content;
	}

	private String stripByteOrderMark(String content) {
		if (!content.isEmpty() && content.charAt(0) == BYTE_ORDER_MARK) {
			return content.substring(1);
		}
		return content;
	}

	private String readAll(File file) throws EnforcerRuleException {
		try {
			return Files.readString(file.toPath());
		} catch (IOException e) {
			throw new EnforcerRuleException("Could not read " + documentName() + " at " + file, e);
		}
	}

	private void verifyTitle(String content) throws EnforcerRuleException {
		if (!content.stripLeading().startsWith(titleHeading())) {
			throw new EnforcerRuleException(
					documentName() + " must start with the '" + titleHeading() + "' title heading");
		}
	}

	private void verifyRequiredSections(String content) throws EnforcerRuleException {
		for (String section : requiredSections()) {
			verifySection(content, section);
		}
	}

	private void verifySection(String content, String section) throws EnforcerRuleException {
		if (!content.contains(section)) {
			throw new EnforcerRuleException(documentName() + " is missing required section heading: " + section);
		}
	}
}

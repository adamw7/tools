package io.github.adamw7.tools.enforcer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Enforcer rule that fails the build when {@code CLAUDE.md} is missing or does
 * not follow the expected structure: it must start with the {@code # CLAUDE.md}
 * title, reference {@code AGENTS.md}, and contain every required section
 * heading.
 */
@Named("claudeMdFormat")
public class ClaudeMdFormatRule extends AbstractEnforcerRule {

	private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
	private static final String TITLE_HEADING = "# CLAUDE.md";
	private static final String AGENTS_REFERENCE = "AGENTS.md";
	private static final List<String> REQUIRED_SECTIONS = List.of(
			"## Project",
			"## Java version",
			"## Maven",
			"## Principles for Java Development",
			"## Testing",
			"## Dependencies");

	/** The {@code CLAUDE.md} file to validate. Injected from the rule configuration. */
	private File claudeMdFile;

	@Override
	public void execute() throws EnforcerRuleException {
		String content = readContent();
		verifyTitle(content);
		verifyAgentsReference(content);
		verifyRequiredSections(content);
	}

	private String readContent() throws EnforcerRuleException {
		if (claudeMdFile == null) {
			throw new EnforcerRuleException("The claudeMdFile parameter is not configured");
		}
		if (!claudeMdFile.isFile()) {
			throw new EnforcerRuleException("CLAUDE.md does not exist at " + claudeMdFile);
		}
		String content = stripByteOrderMark(readAll(claudeMdFile));
		if (content.isBlank()) {
			throw new EnforcerRuleException("CLAUDE.md is empty: " + claudeMdFile);
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
			throw new EnforcerRuleException("Could not read CLAUDE.md at " + file, e);
		}
	}

	private void verifyTitle(String content) throws EnforcerRuleException {
		if (!content.stripLeading().startsWith(TITLE_HEADING)) {
			throw new EnforcerRuleException("CLAUDE.md must start with the '" + TITLE_HEADING + "' title heading");
		}
	}

	private void verifyAgentsReference(String content) throws EnforcerRuleException {
		if (!content.contains(AGENTS_REFERENCE)) {
			throw new EnforcerRuleException("CLAUDE.md must reference " + AGENTS_REFERENCE + " as the source of truth");
		}
	}

	private void verifyRequiredSections(String content) throws EnforcerRuleException {
		for (String section : REQUIRED_SECTIONS) {
			verifySection(content, section);
		}
	}

	private void verifySection(String content, String section) throws EnforcerRuleException {
		if (!content.contains(section)) {
			throw new EnforcerRuleException("CLAUDE.md is missing required section heading: " + section);
		}
	}

	void setClaudeMdFile(File claudeMdFile) {
		this.claudeMdFile = claudeMdFile;
	}

	@Override
	public String toString() {
		return String.format("ClaudeMdFormatRule[claudeMdFile=%s]", claudeMdFile);
	}
}

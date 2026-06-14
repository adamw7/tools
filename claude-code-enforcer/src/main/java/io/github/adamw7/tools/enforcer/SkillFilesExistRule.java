package io.github.adamw7.tools.enforcer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Enforcer rule that fails the build when any skill under the configured skills
 * directory is missing its {@code SKILL.md}, or that file is empty, or it lacks
 * a YAML front matter block with the {@code name} and {@code description} keys
 * a skill requires. Every immediate subdirectory of {@code skillsDir} is treated
 * as a skill. A skills directory with no skills is allowed; all problems found
 * are reported together.
 */
@Named("skillFilesExist")
public class SkillFilesExistRule extends AbstractEnforcerRule {

	private static final String SKILL_FILE_NAME = "SKILL.md";
	private static final String FRONT_MATTER_DELIMITER = "---";
	private static final List<String> REQUIRED_KEYS = List.of("name:", "description:");

	/** The {@code .claude/skills} directory to scan. Injected from the rule configuration. */
	private File skillsDir;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		verifyDirectory();
		List<String> violations = new ArrayList<>();
		for (File skillDirectory : listSkillDirectories()) {
			collectSkillViolations(skillDirectory, violations);
		}
		failIfAny(violations);
	}

	private void verifyConfigured() throws EnforcerRuleException {
		if (skillsDir == null) {
			throw new EnforcerRuleException("The skillsDir parameter is not configured");
		}
	}

	private void verifyDirectory() throws EnforcerRuleException {
		if (!skillsDir.isDirectory()) {
			throw new EnforcerRuleException("Skills directory does not exist at " + skillsDir);
		}
	}

	private File[] listSkillDirectories() {
		File[] skillDirectories = skillsDir.listFiles(File::isDirectory);
		return skillDirectories != null ? skillDirectories : new File[0];
	}

	private void collectSkillViolations(File skillDirectory, List<String> violations) {
		File skillFile = new File(skillDirectory, SKILL_FILE_NAME);
		if (!skillFile.isFile()) {
			violations.add("Missing " + SKILL_FILE_NAME + " in skill directory: " + skillDirectory);
		} else {
			collectContentViolations(skillFile, violations);
		}
	}

	private void collectContentViolations(File skillFile, List<String> violations) {
		String content = readContent(skillFile);
		if (content.isBlank()) {
			violations.add(SKILL_FILE_NAME + " is empty: " + skillFile);
		} else {
			collectFrontMatterViolations(skillFile, content, violations);
		}
	}

	private String readContent(File skillFile) {
		try {
			return MarkdownText.stripByteOrderMark(Files.readString(skillFile.toPath()));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + SKILL_FILE_NAME + " at " + skillFile, e);
		}
	}

	private void collectFrontMatterViolations(File skillFile, String content, List<String> violations) {
		List<String> frontMatter = frontMatter(content);
		if (frontMatter == null) {
			violations.add(SKILL_FILE_NAME + " must start with a YAML front matter block delimited by '"
					+ FRONT_MATTER_DELIMITER + "': " + skillFile);
		} else {
			collectMissingKeys(skillFile, frontMatter, violations);
		}
	}

	private List<String> frontMatter(String content) {
		List<String> lines = content.lines().toList();
		if (!MarkdownText.firstNonBlankLine(content).equals(FRONT_MATTER_DELIMITER)) {
			return null;
		}
		int start = indexOfDelimiter(lines, 0);
		int end = indexOfDelimiter(lines, start + 1);
		return end < 0 ? null : lines.subList(start + 1, end);
	}

	private int indexOfDelimiter(List<String> lines, int from) {
		for (int i = from; i < lines.size(); i++) {
			if (lines.get(i).strip().equals(FRONT_MATTER_DELIMITER)) {
				return i;
			}
		}
		return -1;
	}

	private void collectMissingKeys(File skillFile, List<String> frontMatter, List<String> violations) {
		for (String key : REQUIRED_KEYS) {
			if (!hasKey(frontMatter, key)) {
				violations.add(SKILL_FILE_NAME + " front matter is missing '" + key + "' in: " + skillFile);
			}
		}
	}

	private boolean hasKey(List<String> frontMatter, String key) {
		return frontMatter.stream().anyMatch(line -> line.strip().startsWith(key));
	}

	private void failIfAny(List<String> violations) throws EnforcerRuleException {
		if (!violations.isEmpty()) {
			String separator = System.lineSeparator() + "  - ";
			throw new EnforcerRuleException("Skill files are not well formed:" + separator
					+ String.join(separator, violations));
		}
	}

	void setSkillsDir(File skillsDir) {
		this.skillsDir = skillsDir;
	}

	@Override
	public String toString() {
		return String.format("SkillFilesExistRule[skillsDir=%s]", skillsDir);
	}
}

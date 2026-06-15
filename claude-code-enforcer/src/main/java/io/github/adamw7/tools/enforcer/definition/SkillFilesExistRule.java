package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.FrontMatter;
import io.github.adamw7.tools.enforcer.text.MarkdownText;
import io.github.adamw7.tools.enforcer.text.NameConvention;

/**
 * Enforcer rule that fails the build when any skill under the configured skills
 * directory is malformed. Every immediate subdirectory of {@code skillsDir} is
 * treated as a skill and must contain a non-empty {@code SKILL.md} that opens
 * with a YAML front matter block declaring every required key.
 * <p>
 * The required keys default to {@code name} and {@code description} but can be
 * overridden with {@code requiredKeys}. The {@code name} value is held to the
 * Claude Code naming convention: lower-case kebab-case, at most
 * {@value NameConvention#MAX_LENGTH} characters, and equal to the skill's
 * directory name. The {@code description} must be non-empty and within
 * {@code maxDescriptionLength}. When {@code allowedFrontMatterKeys} is
 * configured, any key outside that set is reported, which catches typos such as
 * {@code descripton}.
 * <p>
 * A skills directory with no skills is allowed; all problems found are reported
 * together.
 */
@Named("skillFilesExist")
public class SkillFilesExistRule extends ClaudeCodeEnforcerRule {

	private static final String SKILL_FILE_NAME = "SKILL.md";
	private static final String NAME_KEY = "name";
	private static final String DESCRIPTION_KEY = "description";
	private static final List<String> DEFAULT_REQUIRED_KEYS = List.of(NAME_KEY, DESCRIPTION_KEY);
	private static final int DEFAULT_MAX_DESCRIPTION_LENGTH = 1024;

	/** The {@code .claude/skills} directory to scan. Injected from the rule configuration. */
	private File skillsDir;

	/** Optional override for the required front matter keys. */
	private List<String> requiredKeys;

	/** Optional whitelist of allowed front matter keys. When set, unknown keys are reported. */
	private List<String> allowedFrontMatterKeys;

	/** Maximum allowed description length. */
	private int maxDescriptionLength = DEFAULT_MAX_DESCRIPTION_LENGTH;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		verifyDirectory();
		List<String> violations = new ArrayList<>();
		for (File skillDirectory : listSkillDirectories()) {
			collectSkillViolations(skillDirectory, violations);
		}
		report("Skill files are not well formed:", violations);
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
			collectContentViolations(skillDirectory, skillFile, violations);
		}
	}

	private void collectContentViolations(File skillDirectory, File skillFile, List<String> violations) {
		String content = readContent(skillFile);
		if (content.isBlank()) {
			violations.add(SKILL_FILE_NAME + " is empty: " + skillFile);
		} else {
			collectFrontMatterViolations(skillDirectory, skillFile, content, violations);
		}
	}

	private String readContent(File skillFile) {
		try {
			return MarkdownText.stripByteOrderMark(Files.readString(skillFile.toPath()));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + SKILL_FILE_NAME + " at " + skillFile, e);
		}
	}

	private void collectFrontMatterViolations(File skillDirectory, File skillFile, String content,
			List<String> violations) {
		Optional<FrontMatter> frontMatter = FrontMatter.parse(content);
		if (frontMatter.isEmpty()) {
			violations.add(SKILL_FILE_NAME + " must start with a YAML front matter block delimited by '---': "
					+ skillFile);
		} else {
			collectFrontMatterViolations(skillDirectory, skillFile, frontMatter.get(), violations);
		}
	}

	private void collectFrontMatterViolations(File skillDirectory, File skillFile, FrontMatter frontMatter,
			List<String> violations) {
		collectMissingKeys(skillFile, frontMatter, violations);
		collectUnknownKeys(skillFile, frontMatter, violations);
		collectNameViolations(skillDirectory, skillFile, frontMatter, violations);
		collectDescriptionViolations(skillFile, frontMatter, violations);
	}

	private void collectMissingKeys(File skillFile, FrontMatter frontMatter, List<String> violations) {
		for (String key : requiredKeys()) {
			if (!frontMatter.hasKey(key)) {
				violations.add(SKILL_FILE_NAME + " front matter is missing '" + key + ":' in: " + skillFile);
			}
		}
	}

	private void collectUnknownKeys(File skillFile, FrontMatter frontMatter, List<String> violations) {
		if (allowedFrontMatterKeys == null) {
			return;
		}
		for (String key : frontMatter.keys()) {
			addUnknownKeyViolation(skillFile, key, violations);
		}
	}

	private void addUnknownKeyViolation(File skillFile, String key, List<String> violations) {
		if (!allowedFrontMatterKeys.contains(key)) {
			violations.add(SKILL_FILE_NAME + " front matter has unknown key '" + key + ":' in: " + skillFile);
		}
	}

	private void collectNameViolations(File skillDirectory, File skillFile, FrontMatter frontMatter,
			List<String> violations) {
		frontMatter.value(NAME_KEY).ifPresent(
				name -> NameConvention.collect(name, skillDirectory.getName(), skillFile.toString(), violations));
	}

	private void collectDescriptionViolations(File skillFile, FrontMatter frontMatter, List<String> violations) {
		frontMatter.value(DESCRIPTION_KEY).ifPresent(
				description -> addDescriptionViolations(skillFile, description, violations));
	}

	private void addDescriptionViolations(File skillFile, String description, List<String> violations) {
		if (description.isBlank()) {
			violations.add(SKILL_FILE_NAME + " description must not be empty in: " + skillFile);
		} else if (description.length() > maxDescriptionLength) {
			violations.add(SKILL_FILE_NAME + " description exceeds " + maxDescriptionLength
					+ " characters in: " + skillFile);
		}
	}

	private List<String> requiredKeys() {
		return requiredKeys != null ? requiredKeys : DEFAULT_REQUIRED_KEYS;
	}

	void setSkillsDir(File skillsDir) {
		this.skillsDir = skillsDir;
	}

	void setRequiredKeys(List<String> requiredKeys) {
		this.requiredKeys = requiredKeys;
	}

	void setAllowedFrontMatterKeys(List<String> allowedFrontMatterKeys) {
		this.allowedFrontMatterKeys = allowedFrontMatterKeys;
	}

	void setMaxDescriptionLength(int maxDescriptionLength) {
		this.maxDescriptionLength = maxDescriptionLength;
	}

	@Override
	public String toString() {
		return String.format("SkillFilesExistRule[skillsDir=%s]", skillsDir);
	}
}

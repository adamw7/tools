package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.inject.Named;

import io.github.adamw7.tools.enforcer.text.NameConvention;

/**
 * Enforcer rule that fails the build when any skill under the configured skills
 * directory is malformed. Every immediate subdirectory of {@code skillsDir} is
 * treated as a skill and must contain a non-empty {@code SKILL.md} that opens with
 * a YAML front matter block declaring every required key.
 * <p>
 * The required keys default to {@code name} and {@code description} but can be
 * overridden with {@code requiredKeys}. The {@code name} is held to the Claude
 * Code naming convention — lower-case kebab-case, at most
 * {@value NameConvention#MAX_LENGTH} characters — and must equal the skill's
 * directory name; the {@code description} must be non-empty and within
 * {@code maxDescriptionLength}. A key outside a configured
 * {@code allowedFrontMatterKeys} is reported, which catches typos such as
 * {@code descripton}. A skills directory with no skills is allowed; all problems
 * found are reported together.
 */
@Named("skillFilesExist")
public class SkillFilesExistRule extends DefinitionFormatRule {

	private static final String SKILL_FILE_NAME = "SKILL.md";
	private static final List<String> DEFAULT_REQUIRED_KEYS = List.of("name", "description");
	private static final int DEFAULT_MAX_DESCRIPTION_LENGTH = 1024;

	/** The {@code .claude/skills} directory to scan. Injected from the rule configuration. */
	private File skillsDir;

	/** Optional override for the required front matter keys. */
	private List<String> requiredKeys;

	/** Optional whitelist of allowed front matter keys. When set, unknown keys are reported. */
	private List<String> allowedFrontMatterKeys;

	/** Maximum allowed description length. */
	private int maxDescriptionLength = DEFAULT_MAX_DESCRIPTION_LENGTH;

	public SkillFilesExistRule() {
		super(new Naming("skillsDir", "Skills", SKILL_FILE_NAME, SKILL_FILE_NAME,
				"Skill files are not well formed:"));
	}

	@Override
	protected File definitionDir() {
		return skillsDir;
	}

	/** A skill is a directory, and the definition it carries is the {@code SKILL.md} inside it. */
	@Override
	protected File[] entriesIn(File directory) {
		return DefinitionFiles.subdirectories(directory);
	}

	@Override
	protected void collectEntryViolations(File skillDirectory, List<String> violations) {
		File skillFile = new File(skillDirectory, SKILL_FILE_NAME);
		if (!skillFile.isFile()) {
			violations.add("Missing " + SKILL_FILE_NAME + " in skill directory: " + skillDirectory);
			return;
		}
		contentOf(skillFile, violations)
				.flatMap(content -> requiredFrontMatterOf(content, skillFile, violations))
				.ifPresent(checks -> collectFrontMatterViolations(skillDirectory, checks));
	}

	private void collectFrontMatterViolations(File skillDirectory, FrontMatterChecks checks) {
		checks.requireKeys(Objects.requireNonNullElse(requiredKeys, DEFAULT_REQUIRED_KEYS));
		checks.rejectDuplicateKeys();
		checks.allowOnlyKeys(allowedFrontMatterKeys);
		checks.checkName(skillDirectory.getName());
		checks.checkDescription(maxDescriptionLength);
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
}

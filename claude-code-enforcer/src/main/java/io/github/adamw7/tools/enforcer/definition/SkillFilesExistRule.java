package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
public class SkillFilesExistRule extends ClaudeCodeEnforcerRule {

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

	/** When true, a malformed front matter block is rewritten in place instead of failing the build. */
	private boolean autoFix;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(skillsDir, "skillsDir");
		DefinitionFiles.verifyDirectory(skillsDir, "Skills");
		List<String> violations = new ArrayList<>();
		for (File skillDirectory : DefinitionFiles.subdirectories(skillsDir)) {
			collectSkillViolations(skillDirectory, violations);
		}
		report("Skill files are not well formed:", violations);
	}

	private void collectSkillViolations(File skillDirectory, List<String> violations) {
		File skillFile = new File(skillDirectory, SKILL_FILE_NAME);
		if (!skillFile.isFile()) {
			violations.add("Missing " + SKILL_FILE_NAME + " in skill directory: " + skillDirectory);
		} else {
			collectContentViolations(skillDirectory, skillFile, violations);
		}
	}

	/**
	 * A {@code SKILL.md} that cannot be decoded as text is reported rather than read.
	 * The rule scans a directory whose contents it does not control, and an
	 * {@link java.io.UncheckedIOException} escaping here would abort the build as an
	 * internal error instead of reporting the malformed skill it exists to catch —
	 * and would take the remaining skills' violations down with it.
	 */
	private void collectContentViolations(File skillDirectory, File skillFile, List<String> violations) {
		Optional<String> text = MarkdownText.readIfText(skillFile);
		if (text.isEmpty()) {
			violations.add(SKILL_FILE_NAME + " cannot be read as text: " + skillFile);
			return;
		}
		String content = text.get();
		if (content.isBlank()) {
			violations.add(SKILL_FILE_NAME + " is empty: " + skillFile);
			return;
		}
		String fixed = FrontMatterAutoFix.apply(skillFile, SKILL_FILE_NAME, content, autoFix, getLog());
		Optional<FrontMatter> frontMatter = FrontMatter.parse(fixed);
		if (frontMatter.isEmpty()) {
			violations.add(SKILL_FILE_NAME + " must start with a YAML front matter block delimited by '---': "
					+ skillFile);
		} else {
			collectFrontMatterViolations(skillDirectory, skillFile, frontMatter.get(), violations);
		}
	}

	private void collectFrontMatterViolations(File skillDirectory, File skillFile, FrontMatter frontMatter,
			List<String> violations) {
		FrontMatterChecks checks = new FrontMatterChecks(frontMatter, SKILL_FILE_NAME, skillFile, violations);
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

	void setAutoFix(boolean autoFix) {
		this.autoFix = autoFix;
	}
}

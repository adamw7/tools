package io.github.adamw7.tools.enforcer;

import java.io.File;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Enforcer rule that fails the build when any skill under the configured skills
 * directory is missing its {@code SKILL.md}. Every immediate subdirectory of
 * {@code skillsDir} is treated as a skill and must contain a {@code SKILL.md}
 * file.
 */
@Named("skillFilesExist")
public class SkillFilesExistRule extends AbstractEnforcerRule {

	private static final String SKILL_FILE_NAME = "SKILL.md";

	/** The {@code .claude/skills} directory to scan. Injected from the rule configuration. */
	private File skillsDir;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		verifyDirectory();
		verifySkillFiles(listSkillDirectories());
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

	private File[] listSkillDirectories() throws EnforcerRuleException {
		File[] skillDirectories = skillsDir.listFiles(File::isDirectory);
		if (skillDirectories == null || skillDirectories.length == 0) {
			throw new EnforcerRuleException("No skill directories found in " + skillsDir);
		}
		return skillDirectories;
	}

	private void verifySkillFiles(File[] skillDirectories) throws EnforcerRuleException {
		for (File skillDirectory : skillDirectories) {
			verifySkillFile(skillDirectory);
		}
	}

	private void verifySkillFile(File skillDirectory) throws EnforcerRuleException {
		File skillFile = new File(skillDirectory, SKILL_FILE_NAME);
		if (!skillFile.isFile()) {
			throw new EnforcerRuleException("Missing " + SKILL_FILE_NAME + " in skill directory: " + skillDirectory);
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

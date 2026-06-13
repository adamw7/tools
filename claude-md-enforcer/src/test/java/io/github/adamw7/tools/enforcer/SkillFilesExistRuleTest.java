package io.github.adamw7.tools.enforcer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillFilesExistRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenEverySkillHasSkillFile() throws IOException {
		createSkill("git-commit");
		createSkill("java-code-review");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void failsWhenFileIsNotConfigured() {
		SkillFilesExistRule rule = new SkillFilesExistRule();

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenDirectoryIsMissing() {
		SkillFilesExistRule rule = ruleFor(tempDir.resolve("absent"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenNoSkillDirectoriesExist() {
		SkillFilesExistRule rule = ruleFor(tempDir);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("No skill directories"), exception.getMessage());
	}

	@Test
	void failsWhenASkillIsMissingItsSkillFile() throws IOException {
		createSkill("git-commit");
		Files.createDirectory(tempDir.resolve("broken-skill"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("Missing SKILL.md"), exception.getMessage());
		assertTrue(exception.getMessage().contains("broken-skill"), exception.getMessage());
	}

	private void createSkill(String name) throws IOException {
		Path skillDir = Files.createDirectory(tempDir.resolve(name));
		Files.writeString(skillDir.resolve("SKILL.md"), "# " + name);
	}

	private SkillFilesExistRule ruleFor(Path skillsDir) {
		SkillFilesExistRule rule = new SkillFilesExistRule();
		rule.setSkillsDir(skillsDir.toFile());
		return rule;
	}
}

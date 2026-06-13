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
	void passesWhenNoSkillDirectoriesExist() {
		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void failsWhenASkillIsMissingItsSkillFile() throws IOException {
		createSkill("git-commit");
		Files.createDirectory(tempDir.resolve("broken-skill"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("Missing SKILL.md"), exception.getMessage());
		assertTrue(exception.getMessage().contains("broken-skill"), exception.getMessage());
	}

	@Test
	void failsWhenSkillFileIsEmpty() throws IOException {
		Path skillDir = Files.createDirectory(tempDir.resolve("empty-skill"));
		Files.writeString(skillDir.resolve("SKILL.md"), "   \n  ");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("is empty"), exception.getMessage());
		assertTrue(exception.getMessage().contains("empty-skill"), exception.getMessage());
	}

	@Test
	void failsWhenSkillFileHasNoFrontMatter() throws IOException {
		Path skillDir = Files.createDirectory(tempDir.resolve("untitled-skill"));
		Files.writeString(skillDir.resolve("SKILL.md"), "# Just a heading, no front matter.");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("front matter"), exception.getMessage());
		assertTrue(exception.getMessage().contains("untitled-skill"), exception.getMessage());
	}

	@Test
	void failsWhenFrontMatterIsMissingARequiredKey() throws IOException {
		Path skillDir = Files.createDirectory(tempDir.resolve("nameless-skill"));
		Files.writeString(skillDir.resolve("SKILL.md"), """
				---
				description: Does a thing.
				---
				# Body
				""");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("name:"), exception.getMessage());
		assertTrue(exception.getMessage().contains("nameless-skill"), exception.getMessage());
	}

	@Test
	void reportsEverySkillProblemTogether() throws IOException {
		Files.createDirectory(tempDir.resolve("no-file"));
		Path empty = Files.createDirectory(tempDir.resolve("empty-skill"));
		Files.writeString(empty.resolve("SKILL.md"), "");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("no-file"), exception.getMessage());
		assertTrue(exception.getMessage().contains("empty-skill"), exception.getMessage());
	}

	private void createSkill(String name) throws IOException {
		Path skillDir = Files.createDirectory(tempDir.resolve(name));
		Files.writeString(skillDir.resolve("SKILL.md"), """
				---
				name: %s
				description: A skill named %s.
				---
				# %s
				""".formatted(name, name, name));
	}

	private SkillFilesExistRule ruleFor(Path skillsDir) {
		SkillFilesExistRule rule = new SkillFilesExistRule();
		rule.setSkillsDir(skillsDir.toFile());
		return rule;
	}
}

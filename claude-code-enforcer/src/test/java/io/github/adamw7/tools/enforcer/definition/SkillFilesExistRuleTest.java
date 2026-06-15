package io.github.adamw7.tools.enforcer.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class SkillFilesExistRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenEverySkillHasSkillFile() {
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
	void failsWhenASkillIsMissingItsSkillFile() {
		createSkill("git-commit");
		createDirectory(tempDir.resolve("broken-skill"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("Missing SKILL.md"), exception.getMessage());
		assertTrue(exception.getMessage().contains("broken-skill"), exception.getMessage());
	}

	@Test
	void failsWhenSkillFileIsEmpty() {
		Path skillDir = createDirectory(tempDir.resolve("empty-skill"));
		writeString(skillDir.resolve("SKILL.md"), "   \n  ");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("is empty"), exception.getMessage());
		assertTrue(exception.getMessage().contains("empty-skill"), exception.getMessage());
	}

	@Test
	void failsWhenSkillFileHasNoFrontMatter() {
		Path skillDir = createDirectory(tempDir.resolve("untitled-skill"));
		writeString(skillDir.resolve("SKILL.md"), "# Just a heading, no front matter.");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("front matter"), exception.getMessage());
		assertTrue(exception.getMessage().contains("untitled-skill"), exception.getMessage());
	}

	@Test
	void failsWhenFrontMatterIsMissingARequiredKey() {
		Path skillDir = createDirectory(tempDir.resolve("nameless-skill"));
		writeString(skillDir.resolve("SKILL.md"), """
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
	void reportsEverySkillProblemTogether() {
		createDirectory(tempDir.resolve("no-file"));
		Path empty = createDirectory(tempDir.resolve("empty-skill"));
		writeString(empty.resolve("SKILL.md"), "");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("no-file"), exception.getMessage());
		assertTrue(exception.getMessage().contains("empty-skill"), exception.getMessage());
	}

	@Test
	void failsWhenNameDoesNotMatchDirectory() {
		Path skillDir = createDirectory(tempDir.resolve("git-commit"));
		writeString(skillDir.resolve("SKILL.md"), """
				---
				name: commit
				description: Mismatched name.
				---
				""");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("must match 'git-commit'"), exception.getMessage());
	}

	@Test
	void failsWhenNameIsNotKebabCase() {
		Path skillDir = createDirectory(tempDir.resolve("Git_Commit"));
		writeString(skillDir.resolve("SKILL.md"), """
				---
				name: Git_Commit
				description: Bad casing.
				---
				""");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("kebab-case"), exception.getMessage());
	}

	@Test
	void failsWhenDescriptionIsEmpty() {
		Path skillDir = createDirectory(tempDir.resolve("git-commit"));
		writeString(skillDir.resolve("SKILL.md"), """
				---
				name: git-commit
				description:
				---
				""");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, ruleFor(tempDir)::execute);
		assertTrue(exception.getMessage().contains("description must not be empty"), exception.getMessage());
	}

	@Test
	void failsWhenDescriptionExceedsConfiguredMaximum() {
		createSkill("git-commit");
		SkillFilesExistRule rule = ruleFor(tempDir);
		rule.setMaxDescriptionLength(5);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("description exceeds 5"), exception.getMessage());
	}

	@Test
	void failsWhenAnUnknownFrontMatterKeyIsPresent() {
		Path skillDir = createDirectory(tempDir.resolve("git-commit"));
		writeString(skillDir.resolve("SKILL.md"), """
				---
				name: git-commit
				descripton: Typo in the key.
				description: Real description.
				---
				""");
		SkillFilesExistRule rule = ruleFor(tempDir);
		rule.setAllowedFrontMatterKeys(java.util.List.of("name", "description"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("unknown key 'descripton:'"), exception.getMessage());
	}

	@Test
	void honoursConfiguredRequiredKeys() {
		Path skillDir = createDirectory(tempDir.resolve("git-commit"));
		writeString(skillDir.resolve("SKILL.md"), """
				---
				name: git-commit
				description: A skill.
				---
				""");
		SkillFilesExistRule rule = ruleFor(tempDir);
		rule.setRequiredKeys(java.util.List.of("name", "description", "model"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing 'model:'"), exception.getMessage());
	}

	@Test
	void warnSeverityLogsInsteadOfFailing() {
		createDirectory(tempDir.resolve("broken-skill"));
		SkillFilesExistRule rule = ruleFor(tempDir);
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("broken-skill")), logger.warnings().toString());
	}

	private void createSkill(String name) {
		Path skillDir = createDirectory(tempDir.resolve(name));
		writeString(skillDir.resolve("SKILL.md"), """
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

	private static void writeString(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	private static Path createDirectory(Path dir) {
		try {
			return Files.createDirectory(dir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create " + dir, e);
		}
	}
}

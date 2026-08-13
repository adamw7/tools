package io.github.adamw7.tools.enforcer.definition;

import static io.github.adamw7.tools.test.TestFiles.createDirectory;
import static io.github.adamw7.tools.test.TestFiles.readString;
import static io.github.adamw7.tools.test.TestFiles.writeBytes;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

		assertFailure(EnforcerRuleException.class, rule::execute, "not configured");
	}

	@Test
	void failsWhenDirectoryIsMissing() {
		SkillFilesExistRule rule = ruleFor(tempDir.resolve("absent"));

		assertFailure(EnforcerRuleException.class, rule::execute, "does not exist");
	}

	@Test
	void passesWhenNoSkillDirectoriesExist() {
		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void failsWhenASkillIsMissingItsSkillFile() {
		createSkill("git-commit");
		createDirectory(tempDir.resolve("broken-skill"));

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "Missing SKILL.md", "broken-skill");
	}

	@Test
	void failsWhenSkillFileIsEmpty() {
		Path skillDir = createDirectory(tempDir.resolve("empty-skill"));
		writeString(skillDir.resolve("SKILL.md"), "   \n  ");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "is empty", "empty-skill");
	}

	@Test
	void failsWhenSkillFileHasNoFrontMatter() {
		Path skillDir = createDirectory(tempDir.resolve("untitled-skill"));
		writeString(skillDir.resolve("SKILL.md"), "# Just a heading, no front matter.");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "front matter", "untitled-skill");
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

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "name:", "nameless-skill");
	}

	@Test
	void reportsEverySkillProblemTogether() {
		createDirectory(tempDir.resolve("no-file"));
		Path empty = createDirectory(tempDir.resolve("empty-skill"));
		writeString(empty.resolve("SKILL.md"), "");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "no-file", "empty-skill");
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

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "must match 'git-commit'");
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

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "kebab-case");
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

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "description must not be empty");
	}

	@Test
	void failsWhenDescriptionExceedsConfiguredMaximum() {
		createSkill("git-commit");
		SkillFilesExistRule rule = ruleFor(tempDir);
		rule.setMaxDescriptionLength(5);

		assertFailure(EnforcerRuleException.class, rule::execute, "description exceeds 5");
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

		assertFailure(EnforcerRuleException.class, rule::execute, "unknown key 'descripton:'");
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

		assertFailure(EnforcerRuleException.class, rule::execute, "missing 'model:'");
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

	@Test
	void autoFixRepairsAMissingClosingDelimiter() {
		Path skillDir = createDirectory(tempDir.resolve("git-commit"));
		Path file = skillDir.resolve("SKILL.md");
		writeString(file, """
				---
				name: git-commit
				description: A skill named git-commit.
				# Body
				""");
		SkillFilesExistRule rule = ruleFor(tempDir);
		rule.setAutoFix(true);
		rule.setLog(new CapturingLogger());

		assertDoesNotThrow(rule::execute);
		assertTrue(readString(file).contains("---\n# Body"), readString(file));
	}

	@Test
	void acceptsADescriptionWrittenAsABlockScalar() {
		Path skillDir = createDirectory(tempDir.resolve("git-commit"));
		writeString(skillDir.resolve("SKILL.md"), """
				---
				name: git-commit
				description: >
				  Generate conventional commit messages. Use when: the user says "commit".
				---
				# Body
				""");
		SkillFilesExistRule rule = ruleFor(tempDir);
		rule.setAllowedFrontMatterKeys(java.util.List.of("name", "description"));

		// The wrapped prose continues the description, so "Use when" is not an
		// unknown key the skill declared.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void measuresABlockScalarDescriptionAgainstTheLengthCap() {
		Path skillDir = createDirectory(tempDir.resolve("git-commit"));
		writeString(skillDir.resolve("SKILL.md"), """
				---
				name: git-commit
				description: >
				  %s
				---
				# Body
				""".formatted("x".repeat(60)));
		SkillFilesExistRule rule = ruleFor(tempDir);
		rule.setMaxDescriptionLength(20);

		assertFailure(EnforcerRuleException.class, rule::execute, "exceeds 20 characters");
	}

	/** Quoting a value is ordinary YAML, so the name inside the quotes is the one checked. */
	@Test
	void acceptsAQuotedName() {
		Path skillDir = createDirectory(tempDir.resolve("git-commit"));
		writeString(skillDir.resolve("SKILL.md"), """
				---
				name: "git-commit"
				description: "Commits. Use when: committing."
				---
				# git-commit
				""");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
	}

	@Test
	void reportsASkillFileThatCannotBeReadAsText() {
		Path skillDir = createDirectory(tempDir.resolve("binary"));
		writeBytes(skillDir.resolve("SKILL.md"), new byte[] { (byte) 0xC3, (byte) 0x28 });

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute, "cannot be read as text");
	}

	/**
	 * Only the last declaration of a repeated key takes effect, so an earlier one is
	 * a line the author wrote and Claude Code never reads.
	 */
	@Test
	void failsWhenAKeyIsDeclaredTwice() {
		writeString(tempDir.resolve("git-commit").resolve("SKILL.md"), """
				---
				name: git-commit
				description: Writes a commit message.
				description: Writes a commit message, at length.
				---
				# git-commit
				""");

		assertFailure(EnforcerRuleException.class, ruleFor(tempDir)::execute,
				"declares 'description:' more than once");
	}

	/** The cap applies to the declaration that wins, not to the one it replaced. */
	@Test
	void measuresTheDescriptionCapAgainstTheLastDeclaration() {
		writeString(tempDir.resolve("git-commit").resolve("SKILL.md"), """
				---
				name: git-commit
				description: Short.
				description: Far longer than the cap allows on any reading of it.
				---
				# git-commit
				""");
		SkillFilesExistRule rule = ruleFor(tempDir);
		rule.setMaxDescriptionLength(20);

		assertFailure(EnforcerRuleException.class, rule::execute, "description exceeds 20 characters");
	}

	/** A YAML loader ends a plain scalar at the comment, so the name Claude Code reads follows the convention. */
	@Test
	void ignoresATrailingCommentOnTheName() {
		writeString(tempDir.resolve("git-commit").resolve("SKILL.md"), """
				---
				name: git-commit # the repository's commit helper
				description: Generates commit messages.
				---
				# git-commit
				""");

		assertDoesNotThrow(ruleFor(tempDir)::execute);
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
}

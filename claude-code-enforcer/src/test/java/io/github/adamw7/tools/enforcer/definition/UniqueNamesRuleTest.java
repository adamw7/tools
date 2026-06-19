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

class UniqueNamesRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenEveryNameIsUnique() {
		Path commands = createDir("commands");
		Path agents = createDir("agents");
		Path skills = createDir("skills");
		writeMarkdown(commands.resolve("commit.md"));
		writeMarkdown(agents.resolve("reviewer.md"));
		createSkill(skills, "planner");

		assertDoesNotThrow(ruleFor(commands, agents, skills)::execute);
	}

	@Test
	void passesWhenNoDefinitionsExist() {
		Path commands = createDir("commands");
		Path agents = createDir("agents");
		Path skills = createDir("skills");

		assertDoesNotThrow(ruleFor(commands, agents, skills)::execute);
	}

	@Test
	void passesWhenOnlyOneDirectoryIsConfigured() {
		Path skills = createDir("skills");
		createSkill(skills, "commit");
		createSkill(skills, "review");

		UniqueNamesRule rule = new UniqueNamesRule();
		rule.setSkillsDir(skills.toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void ignoresNonMarkdownFiles() {
		Path commands = createDir("commands");
		writeString(commands.resolve("notes.txt"), "not a command");

		UniqueNamesRule rule = new UniqueNamesRule();
		rule.setCommandsDir(commands.toFile());

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, new UniqueNamesRule()::execute);
		assertTrue(exception.getMessage().contains("must be configured"), exception.getMessage());
	}

	@Test
	void failsWhenConfiguredDirectoryIsMissing() {
		UniqueNamesRule rule = new UniqueNamesRule();
		rule.setCommandsDir(tempDir.resolve("absent").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenACommandAndASubAgentShareAName() {
		Path commands = createDir("commands");
		Path agents = createDir("agents");
		writeMarkdown(commands.resolve("review.md"));
		writeMarkdown(agents.resolve("review.md"));

		UniqueNamesRule rule = new UniqueNamesRule();
		rule.setCommandsDir(commands.toFile());
		rule.setAgentsDir(agents.toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("name 'review'"), exception.getMessage());
		assertTrue(exception.getMessage().contains("2 definitions"), exception.getMessage());
		assertTrue(exception.getMessage().contains(commands.resolve("review.md").toString()), exception.getMessage());
		assertTrue(exception.getMessage().contains(agents.resolve("review.md").toString()), exception.getMessage());
	}

	@Test
	void failsWhenASubAgentAndASkillShareAName() {
		Path agents = createDir("agents");
		Path skills = createDir("skills");
		writeMarkdown(agents.resolve("commit.md"));
		createSkill(skills, "commit");

		UniqueNamesRule rule = new UniqueNamesRule();
		rule.setAgentsDir(agents.toFile());
		rule.setSkillsDir(skills.toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("name 'commit'"), exception.getMessage());
	}

	@Test
	void downgradesClashToAWarningWhenSeverityIsWarn() {
		Path commands = createDir("commands");
		Path agents = createDir("agents");
		writeMarkdown(commands.resolve("review.md"));
		writeMarkdown(agents.resolve("review.md"));

		UniqueNamesRule rule = new UniqueNamesRule();
		rule.setCommandsDir(commands.toFile());
		rule.setAgentsDir(agents.toFile());
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("name 'review'")),
				logger.warnings().toString());
	}

	private UniqueNamesRule ruleFor(Path commandsDir, Path agentsDir, Path skillsDir) {
		UniqueNamesRule rule = new UniqueNamesRule();
		rule.setCommandsDir(commandsDir.toFile());
		rule.setAgentsDir(agentsDir.toFile());
		rule.setSkillsDir(skillsDir.toFile());
		return rule;
	}

	private Path createDir(String name) {
		try {
			return Files.createDirectories(tempDir.resolve(name));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create " + name, e);
		}
	}

	private void createSkill(Path skillsDir, String name) {
		Path skill = skillsDir.resolve(name);
		try {
			Files.createDirectories(skill);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create skill " + name, e);
		}
		writeString(skill.resolve("SKILL.md"), "# " + name);
	}

	private void writeMarkdown(Path file) {
		writeString(file, "# " + file.getFileName());
	}

	private static void writeString(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}
}

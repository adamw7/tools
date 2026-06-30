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

class UniqueDescriptionsRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenDescriptionsAreUnique() {
		writeAgent("reviewer", "Reviews code.");
		writeAgent("planner", "Plans the work.");

		assertDoesNotThrow(agentsRule()::execute);
	}

	@Test
	void passesWhenNoDefinitionsExist() {
		assertDoesNotThrow(agentsRule()::execute);
	}

	@Test
	void failsWhenTwoAgentsShareADescription() {
		writeAgent("reviewer", "Reviews code.");
		writeAgent("checker", "Reviews code.");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, agentsRule()::execute);
		assertTrue(exception.getMessage().contains("Reviews code."), exception.getMessage());
		assertTrue(exception.getMessage().contains("reviewer.md"), exception.getMessage());
		assertTrue(exception.getMessage().contains("checker.md"), exception.getMessage());
	}

	@Test
	void ignoresCaseAndWhitespaceWhenComparing() {
		writeAgent("reviewer", "Reviews   code.");
		writeAgent("checker", "reviews code.");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, agentsRule()::execute);
		assertTrue(exception.getMessage().contains("is used by 2"), exception.getMessage());
	}

	@Test
	void catchesAClashBetweenAnAgentAndASkill() {
		writeAgent("reviewer", "Reviews code.");
		writeSkill("inspect", "Reviews code.");
		UniqueDescriptionsRule rule = new UniqueDescriptionsRule();
		rule.setAgentsDir(agentsDir().toFile());
		rule.setSkillsDir(skillsDir().toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("is used by 2"), exception.getMessage());
	}

	@Test
	void skipsBlankAndMissingDescriptions() {
		writeAgent("reviewer", " ");
		writeAgentWithoutDescription("planner");

		assertDoesNotThrow(agentsRule()::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				new UniqueDescriptionsRule()::execute);
		assertTrue(exception.getMessage().contains("must be configured"), exception.getMessage());
	}

	@Test
	void failsWhenDirectoryIsMissing() {
		UniqueDescriptionsRule rule = new UniqueDescriptionsRule();
		rule.setAgentsDir(tempDir.resolve("absent").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void warnsInsteadOfFailingWhenSeverityIsWarn() {
		writeAgent("reviewer", "Reviews code.");
		writeAgent("checker", "Reviews code.");
		UniqueDescriptionsRule rule = agentsRule();
		rule.setSeverity("warn");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("Reviews code.")), logger.warnings().toString());
	}

	private UniqueDescriptionsRule agentsRule() {
		UniqueDescriptionsRule rule = new UniqueDescriptionsRule();
		rule.setAgentsDir(agentsDir().toFile());
		return rule;
	}

	private void writeAgent(String name, String description) {
		writeString(agentsDir().resolve(name + ".md"), """
				---
				name: %s
				description: %s
				---
				# %s
				""".formatted(name, description, name));
	}

	private void writeAgentWithoutDescription(String name) {
		writeString(agentsDir().resolve(name + ".md"), """
				---
				name: %s
				---
				# %s
				""".formatted(name, name));
	}

	private void writeSkill(String name, String description) {
		writeString(skillsDir().resolve(name).resolve("SKILL.md"), """
				---
				name: %s
				description: %s
				---
				# %s
				""".formatted(name, description, name));
	}

	private Path agentsDir() {
		return createDirectories(tempDir.resolve("agents"));
	}

	private Path skillsDir() {
		return createDirectories(tempDir.resolve("skills"));
	}

	private void writeString(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	private static Path createDirectories(Path dir) {
		try {
			return Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create " + dir, e);
		}
	}
}

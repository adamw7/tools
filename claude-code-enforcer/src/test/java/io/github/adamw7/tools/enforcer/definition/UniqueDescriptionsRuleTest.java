package io.github.adamw7.tools.enforcer.definition;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.createDirectory;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	void doesNotTreatTwoBlockScalarDescriptionsAsTheSameOne() {
		writeAgentWithBlockScalarDescription("reviewer", "Reviews Java code for defects.");
		writeAgentWithBlockScalarDescription("committer", "Writes conventional commit messages.");

		// Both declare their description as '>'; reading the indicator instead of the
		// prose below it would make every such definition look identical.
		assertDoesNotThrow(agentsRule()::execute);
	}

	@Test
	void stillReportsTwoBlockScalarDescriptionsThatDoAgree() {
		writeAgentWithBlockScalarDescription("reviewer", "Reviews Java code for defects.");
		writeAgentWithBlockScalarDescription("auditor", "Reviews Java code for defects.");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, agentsRule()::execute);
		assertTrue(exception.getMessage().contains("Reviews Java code for defects."), exception.getMessage());
	}

	private void writeAgentWithBlockScalarDescription(String name, String description) {
		writeString(agentsDir().resolve(name + ".md"), """
				---
				name: %s
				description: >
				  %s
				---
				# %s
				""".formatted(name, description, name));
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
		return createDirectory(tempDir.resolve("agents"));
	}

	private Path skillsDir() {
		return createDirectory(tempDir.resolve("skills"));
	}
}

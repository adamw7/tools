package io.github.adamw7.tools.enforcer.definition;

import static io.github.adamw7.tools.test.TestFiles.createDirectory;
import static io.github.adamw7.tools.test.TestFiles.writeBytes;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Locale;

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

		assertFailure(EnforcerRuleException.class, agentsRule()::execute, "Reviews code.", "reviewer.md",
				"checker.md");
	}

	@Test
	void ignoresCaseAndWhitespaceWhenComparing() {
		writeAgent("reviewer", "Reviews   code.");
		writeAgent("checker", "reviews code.");

		assertFailure(EnforcerRuleException.class, agentsRule()::execute, "is used by 2");
	}

	/**
	 * Case is folded in the root locale, so which descriptions count as duplicates is
	 * a property of the definitions rather than of the machine running the build.
	 * Turkish folds {@code I} to a dotless {@code ı}, which made these two collide
	 * there and nowhere else — a rule failing on one developer's machine and passing
	 * on CI.
	 */
	@Test
	void comparesDescriptionsTheSameWayInEveryLocale() {
		writeAgent("installer", "Installs the CLI.");
		writeAgent("inspector", "ınstalls the clı.");
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(Locale.of("tr", "TR"));
			assertDoesNotThrow(agentsRule()::execute);
		} finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void catchesAClashBetweenAnAgentAndASkill() {
		writeAgent("reviewer", "Reviews code.");
		writeSkill("inspect", "Reviews code.");
		UniqueDescriptionsRule rule = new UniqueDescriptionsRule();
		rule.setAgentsDir(agentsDir().toFile());
		rule.setSkillsDir(skillsDir().toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "is used by 2");
	}

	@Test
	void skipsBlankAndMissingDescriptions() {
		writeAgent("reviewer", " ");
		writeAgentWithoutDescription("planner");

		assertDoesNotThrow(agentsRule()::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		assertFailure(EnforcerRuleException.class, new UniqueDescriptionsRule()::execute, "must be configured");
	}

	@Test
	void failsWhenDirectoryIsMissing() {
		UniqueDescriptionsRule rule = new UniqueDescriptionsRule();
		rule.setAgentsDir(tempDir.resolve("absent").toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "does not exist");
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

		assertFailure(EnforcerRuleException.class, agentsRule()::execute, "Reviews Java code for defects.");
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

	/** The format rules report the unreadable file; this one must not abort the build over it. */
	@Test
	void skipsADefinitionThatCannotBeReadAsText() {
		writeAgent("reviewer", "Reviews code.");
		writeBytes(agentsDir().resolve("binary.md"), new byte[] { (byte) 0xC3, (byte) 0x28 });

		assertDoesNotThrow(agentsRule()::execute);
	}

	/** A quoted description is the same description as its unquoted spelling. */
	@Test
	void comparesAQuotedDescriptionByItsText() {
		writeAgent("reviewer", "Reviews code.");
		writeAgent("checker", "\"Reviews code.\"");

		assertFailure(EnforcerRuleException.class, agentsRule()::execute, "checker.md");
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

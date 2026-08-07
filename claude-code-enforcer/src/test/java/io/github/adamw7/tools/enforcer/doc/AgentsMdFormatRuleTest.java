package io.github.adamw7.tools.enforcer.doc;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentsMdFormatRuleTest {

	private static final String VALID_CONTENT = """
			# AGENTS.md

			Guidance for AI coding agents working in this repository.

			## Project overview
			A library of Java tooling.

			## Module layout
			A multi-module Maven project.

			## Environment & toolchain
			Java 25 and Maven 3.9.x.

			## Build, test, and run
			Build from the repository root.

			## Code style & conventions
			SOLID principles and clean code.

			## Releasing
			Bump the revision property.

			## Pull requests & commits
			Use conventional commit messages.
			""";

	@TempDir
	private Path tempDir;

	@Test
	void passesForWellFormedFile() {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenFileStartsWithByteOrderMark() {
		AgentsMdFormatRule rule = ruleFor((char) 0xFEFF + VALID_CONTENT);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenFileIsNotConfigured() {
		AgentsMdFormatRule rule = new AgentsMdFormatRule();

		assertFailure(EnforcerRuleException.class, rule::execute, "not configured");
	}

	@Test
	void failsWhenFileIsMissing() {
		AgentsMdFormatRule rule = new AgentsMdFormatRule();
		rule.setAgentsMdFile(tempDir.resolve("absent.md").toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "does not exist");
	}

	@Test
	void failsWhenFileIsEmpty() {
		AgentsMdFormatRule rule = ruleFor("   \n  ");

		assertFailure(EnforcerRuleException.class, rule::execute, "empty");
	}

	@Test
	void failsWhenTitleHeadingIsWrong() {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# AGENTS.md", "# Something Else"));

		assertFailure(EnforcerRuleException.class, rule::execute, "title heading");
	}

	@Test
	void failsWhenARequiredSectionIsMissing() {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Releasing", "## Shipping"));

		assertFailure(EnforcerRuleException.class, rule::execute, "## Releasing");
	}

	@Test
	void failsWhenSectionHeadingAppearsOnlyInsideCodeFence() {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Releasing", "```\n## Releasing\n```"));

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required section heading: ## Releasing");
	}

	@Test
	void honoursConfiguredTitleAndSections() {
		String content = """
				# Custom Guide

				## Only Section
				Some content.
				""";
		AgentsMdFormatRule rule = ruleFor(content);
		rule.setTitleHeading("# Custom Guide");
		rule.setRequiredSections(java.util.List.of("## Only Section"));

		assertDoesNotThrow(rule::execute);
	}

	private AgentsMdFormatRule ruleFor(String content) {
		Path file = tempDir.resolve("AGENTS.md");
		writeString(file, content);
		AgentsMdFormatRule rule = new AgentsMdFormatRule();
		rule.setAgentsMdFile(file.toFile());
		return rule;
	}

}

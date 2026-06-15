package io.github.adamw7.tools.enforcer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenFileIsMissing() {
		AgentsMdFormatRule rule = new AgentsMdFormatRule();
		rule.setAgentsMdFile(tempDir.resolve("absent.md").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenFileIsEmpty() {
		AgentsMdFormatRule rule = ruleFor("   \n  ");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("empty"), exception.getMessage());
	}

	@Test
	void failsWhenTitleHeadingIsWrong() {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# AGENTS.md", "# Something Else"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("title heading"), exception.getMessage());
	}

	@Test
	void failsWhenARequiredSectionIsMissing() {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Releasing", "## Shipping"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("## Releasing"), exception.getMessage());
	}

	@Test
	void failsWhenSectionHeadingAppearsOnlyInsideCodeFence() {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Releasing", "```\n## Releasing\n```"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing required section heading: ## Releasing"),
				exception.getMessage());
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

	private static void writeString(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}
}

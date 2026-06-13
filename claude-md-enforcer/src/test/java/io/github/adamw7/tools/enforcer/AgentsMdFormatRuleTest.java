package io.github.adamw7.tools.enforcer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
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
	void passesForWellFormedFile() throws IOException {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenFileStartsWithByteOrderMark() throws IOException {
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
	void failsWhenFileIsEmpty() throws IOException {
		AgentsMdFormatRule rule = ruleFor("   \n  ");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("empty"), exception.getMessage());
	}

	@Test
	void failsWhenTitleHeadingIsWrong() throws IOException {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# AGENTS.md", "# Something Else"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("title heading"), exception.getMessage());
	}

	@Test
	void failsWhenARequiredSectionIsMissing() throws IOException {
		AgentsMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Releasing", "## Shipping"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("## Releasing"), exception.getMessage());
	}

	private AgentsMdFormatRule ruleFor(String content) throws IOException {
		Path file = tempDir.resolve("AGENTS.md");
		Files.writeString(file, content);
		AgentsMdFormatRule rule = new AgentsMdFormatRule();
		rule.setAgentsMdFile(file.toFile());
		return rule;
	}
}

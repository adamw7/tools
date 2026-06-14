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

class ClaudeMdFormatRuleTest {

	private static final String VALID_CONTENT = """
			# CLAUDE.md

			See [AGENTS.md](AGENTS.md) for the full agent guide.

			## Project
			Java project built with Maven.

			## Java version
			Java 25.

			## Maven
			Versions live in the root pom.

			## Principles for Java Development
			Use SOLID Principles.

			## Testing
			Write unit tests for all new logic.

			## Dependencies
			Ask before adding a new one.
			""";

	@TempDir
	private Path tempDir;

	@Test
	void passesForWellFormedFile() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenFileStartsWithByteOrderMark() throws IOException {
		ClaudeMdFormatRule rule = ruleFor((char) 0xFEFF + VALID_CONTENT);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenFileIsNotConfigured() {
		ClaudeMdFormatRule rule = new ClaudeMdFormatRule();

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("not configured"), exception.getMessage());
	}

	@Test
	void failsWhenFileIsMissing() {
		ClaudeMdFormatRule rule = new ClaudeMdFormatRule();
		rule.setClaudeMdFile(tempDir.resolve("absent.md").toFile());

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("does not exist"), exception.getMessage());
	}

	@Test
	void failsWhenFileIsEmpty() throws IOException {
		ClaudeMdFormatRule rule = ruleFor("   \n  ");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("empty"), exception.getMessage());
	}

	@Test
	void failsWhenTitleHeadingIsWrong() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# Something Else"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("title heading"), exception.getMessage());
	}

	@Test
	void failsWhenAgentsReferenceIsMissing() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("AGENTS.md", "OTHER.md"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("AGENTS.md"), exception.getMessage());
	}

	@Test
	void failsWhenARequiredSectionIsMissing() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "## Quality"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("## Testing"), exception.getMessage());
	}

	@Test
	void failsWhenSectionHeadingAppearsOnlyInsideCodeFence() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "```\n## Testing\n```"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing required section heading: ## Testing"),
				exception.getMessage());
	}

	@Test
	void failsWhenTitleIsOnlyAPartialMatch() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# CLAUDE.md-extended"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("title heading"), exception.getMessage());
	}

	@Test
	void failsWhenARequiredSectionIsEmpty() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("Ask before adding a new one.", ""));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("empty section: ## Dependencies"), exception.getMessage());
	}

	@Test
	void passesWhenASectionContainsOnlySubsections() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace(
				"## Maven\nVersions live in the root pom.",
				"## Maven\n### Versions\nVersions live in the root pom."));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenRealSectionIsEmptyDespiteFencedDuplicate() throws IOException {
		String content = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full agent guide.

				```
				## Dependencies
				Ask before adding a new one.
				```

				## Project
				Java project built with Maven.

				## Java version
				Java 25.

				## Maven
				Versions live in the root pom.

				## Principles for Java Development
				Use SOLID Principles.

				## Testing
				Write unit tests for all new logic.

				## Dependencies
				""";
		ClaudeMdFormatRule rule = ruleFor(content);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("empty section: ## Dependencies"), exception.getMessage());
	}

	@Test
	void reportsEveryProblemTogether() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# Wrong").replace("## Maven", "## Build"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("title heading"), exception.getMessage());
		assertTrue(exception.getMessage().contains("## Maven"), exception.getMessage());
	}

	@Test
	void failsWhenAForbiddenTokenAppears() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nTODO: finish this.\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("forbidden token: TODO"), exception.getMessage());
	}

	@Test
	void ignoresAForbiddenTokenInsideACodeFence() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n```\nTODO inside code\n```\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenSectionsAreInConfiguredOrder() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT);
		rule.setEnforceSectionOrder(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenRequiredSectionsAppearInTheWrongOrder() throws IOException {
		String reordered = """
				# CLAUDE.md

				See [AGENTS.md](AGENTS.md) for the full agent guide.

				## Project
				Java project built with Maven.

				## Java version
				Java 25.

				## Maven
				Versions live in the root pom.

				## Principles for Java Development
				Use SOLID Principles.

				## Dependencies
				Ask before adding a new one.

				## Testing
				Write unit tests for all new logic.
				""";
		ClaudeMdFormatRule rule = ruleFor(reordered);
		rule.setEnforceSectionOrder(true);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("out of order"), exception.getMessage());
	}

	@Test
	void failsWhenALineExceedsTheMaximumLength() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n" + "x".repeat(200) + "\n");
		rule.setMaxLineLength(120);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("exceeds 120 characters"), exception.getMessage());
	}

	@Test
	void failsWhenAFileReferenceIsMissing() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT);
		rule.setValidateFileReferences(true);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing file: AGENTS.md"), exception.getMessage());
	}

	@Test
	void passesWhenReferencedFilesExist() throws IOException {
		Files.writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT);
		rule.setValidateFileReferences(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void ignoresLinkTitlesWhenValidatingReferences() throws IOException {
		Files.writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nSee [guide](AGENTS.md \"The agent guide\").\n");
		rule.setValidateFileReferences(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void ignoresExternalLinksWhenValidatingReferences() throws IOException {
		Files.writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nSee [site](https://example.com) and [top](#project).\n");
		rule.setValidateFileReferences(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void warnSeverityLogsInsteadOfFailing() throws IOException {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# Wrong"));
		rule.setSeverity("WARN");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("title heading")), logger.warnings().toString());
	}

	private ClaudeMdFormatRule ruleFor(String content) throws IOException {
		Path file = tempDir.resolve("CLAUDE.md");
		Files.writeString(file, content);
		ClaudeMdFormatRule rule = new ClaudeMdFormatRule();
		rule.setClaudeMdFile(file.toFile());
		return rule;
	}
}

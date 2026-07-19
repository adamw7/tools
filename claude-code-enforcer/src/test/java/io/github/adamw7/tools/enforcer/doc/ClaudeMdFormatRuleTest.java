package io.github.adamw7.tools.enforcer.doc;

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

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

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
	void passesForWellFormedFile() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenFileStartsWithByteOrderMark() {
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
	void failsWhenFileIsEmpty() {
		ClaudeMdFormatRule rule = ruleFor("   \n  ");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("empty"), exception.getMessage());
	}

	@Test
	void failsWhenTitleHeadingIsWrong() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# Something Else"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("title heading"), exception.getMessage());
	}

	@Test
	void failsWhenAgentsReferenceIsMissing() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("AGENTS.md", "OTHER.md"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("AGENTS.md"), exception.getMessage());
	}

	@Test
	void passesGenericFileWhenSectionsAndAgentsReferenceDisabled() {
		String generic = """
				# CLAUDE.md

				This file guides Claude Code when working in this repository.

				## Build
				Run the project's build to compile and test.
				""";
		ClaudeMdFormatRule rule = ruleFor(generic);
		rule.setEnforceRequiredSections(false);
		rule.setRequireAgentsReference(false);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void stillEnforcesTitleWhenSectionsAndAgentsReferenceDisabled() {
		ClaudeMdFormatRule rule = ruleFor("# Not the title\n\nSome content.\n");
		rule.setEnforceRequiredSections(false);
		rule.setRequireAgentsReference(false);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("title heading"), exception.getMessage());
	}

	@Test
	void skipsAgentsReferenceCheckWhenDisabled() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("AGENTS.md", "OTHER.md"));
		rule.setRequireAgentsReference(false);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void skipsMissingSectionWhenRequiredSectionsDisabled() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "## Quality"));
		rule.setEnforceRequiredSections(false);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenARequiredSectionIsMissing() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "## Quality"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("## Testing"), exception.getMessage());
	}

	@Test
	void failsWhenSectionHeadingAppearsOnlyInsideCodeFence() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "```\n## Testing\n```"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing required section heading: ## Testing"),
				exception.getMessage());
	}

	@Test
	void failsWhenTitleIsOnlyAPartialMatch() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# CLAUDE.md-extended"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("title heading"), exception.getMessage());
	}

	@Test
	void failsWhenARequiredSectionIsEmpty() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("Ask before adding a new one.", ""));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("empty section: ## Dependencies"), exception.getMessage());
	}

	@Test
	void passesWhenASectionContainsOnlySubsections() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace(
				"## Maven\nVersions live in the root pom.",
				"## Maven\n### Versions\nVersions live in the root pom."));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenRealSectionIsEmptyDespiteFencedDuplicate() {
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
	void reportsEveryProblemTogether() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# Wrong").replace("## Maven", "## Build"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("title heading"), exception.getMessage());
		assertTrue(exception.getMessage().contains("## Maven"), exception.getMessage());
	}

	@Test
	void failsWhenAForbiddenTokenAppears() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nTODO: finish this.\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("forbidden token: TODO"), exception.getMessage());
	}

	@Test
	void ignoresAForbiddenTokenInsideACodeFence() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n```\nTODO inside code\n```\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void ignoresAForbiddenTokenInsideATildeFence() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n~~~\nTODO inside code\n~~~\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenSectionHeadingAppearsOnlyInsideTildeFence() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "~~~\n## Testing\n~~~"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing required section heading: ## Testing"),
				exception.getMessage());
	}

	@Test
	void treatsABacktickLineInsideATildeFenceAsCode() {
		// The ``` does not close the ~~~ block, so the ## Testing heading between the
		// two backtick lines stays code and the required section is reported missing.
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "~~~\n```\n## Testing\n```\n~~~"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing required section heading: ## Testing"),
				exception.getMessage());
	}

	@Test
	void passesWhenSectionsAreInConfiguredOrder() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT);
		rule.setEnforceSectionOrder(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenRequiredSectionsAppearInTheWrongOrder() {
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
	void doesNotReportOutOfOrderWhenARequiredSectionAppearsTwice() {
		String duplicated = VALID_CONTENT + """
				## Testing
				More notes on testing.
				""";
		ClaudeMdFormatRule rule = ruleFor(duplicated);
		rule.setEnforceSectionOrder(true);

		// The sections are in the configured order; a repeated "## Testing" must be
		// counted once by its first occurrence, not flagged as out of order.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenALineExceedsTheMaximumLength() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n" + "x".repeat(200) + "\n");
		rule.setMaxLineLength(120);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("exceeds 120 characters"), exception.getMessage());
	}

	@Test
	void failsWhenAFileReferenceIsMissing() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT);
		rule.setValidateFileReferences(true);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("missing file: AGENTS.md"), exception.getMessage());
	}

	@Test
	void passesWhenReferencedFilesExist() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT);
		rule.setValidateFileReferences(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void ignoresLinkTitlesWhenValidatingReferences() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nSee [guide](AGENTS.md \"The agent guide\").\n");
		rule.setValidateFileReferences(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void ignoresExternalLinksWhenValidatingReferences() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nSee [site](https://example.com) and [top](#project).\n");
		rule.setValidateFileReferences(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void warnSeverityLogsInsteadOfFailing() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# Wrong"));
		rule.setSeverity("WARN");
		CapturingLogger logger = new CapturingLogger();
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertTrue(logger.warnings().stream().anyMatch(w -> w.contains("title heading")), logger.warnings().toString());
	}

	private ClaudeMdFormatRule ruleFor(String content) {
		Path file = tempDir.resolve("CLAUDE.md");
		writeString(file, content);
		ClaudeMdFormatRule rule = new ClaudeMdFormatRule();
		rule.setClaudeMdFile(file.toFile());
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

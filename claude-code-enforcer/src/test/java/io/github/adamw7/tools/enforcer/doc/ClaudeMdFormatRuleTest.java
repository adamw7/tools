package io.github.adamw7.tools.enforcer.doc;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

		assertFailure(EnforcerRuleException.class, rule::execute, "not configured");
	}

	@Test
	void failsWhenFileIsMissing() {
		ClaudeMdFormatRule rule = new ClaudeMdFormatRule();
		rule.setClaudeMdFile(tempDir.resolve("absent.md").toFile());

		assertFailure(EnforcerRuleException.class, rule::execute, "does not exist");
	}

	@Test
	void failsWhenFileIsEmpty() {
		ClaudeMdFormatRule rule = ruleFor("   \n  ");

		assertFailure(EnforcerRuleException.class, rule::execute, "empty");
	}

	@Test
	void failsWhenTitleHeadingIsWrong() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# Something Else"));

		assertFailure(EnforcerRuleException.class, rule::execute, "title heading");
	}

	@Test
	void failsWhenAgentsReferenceIsMissing() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("AGENTS.md", "OTHER.md"));

		assertFailure(EnforcerRuleException.class, rule::execute, "AGENTS.md");
	}

	@Test
	void failsWhenARequiredSectionIsMissing() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "## Quality"));

		assertFailure(EnforcerRuleException.class, rule::execute, "## Testing");
	}

	@Test
	void failsWhenSectionHeadingAppearsOnlyInsideCodeFence() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "```\n## Testing\n```"));

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required section heading: ## Testing");
	}

	@Test
	void failsWhenTitleIsOnlyAPartialMatch() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# CLAUDE.md-extended"));

		assertFailure(EnforcerRuleException.class, rule::execute, "title heading");
	}

	@Test
	void failsWhenARequiredSectionIsEmpty() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("Ask before adding a new one.", ""));

		assertFailure(EnforcerRuleException.class, rule::execute, "empty section: ## Dependencies");
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

		assertFailure(EnforcerRuleException.class, rule::execute, "empty section: ## Dependencies");
	}

	@Test
	void reportsEveryProblemTogether() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("# CLAUDE.md", "# Wrong").replace("## Maven", "## Build"));

		assertFailure(EnforcerRuleException.class, rule::execute, "title heading", "## Maven");
	}

	@Test
	void failsWhenAForbiddenTokenAppears() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nTODO: finish this.\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		assertFailure(EnforcerRuleException.class, rule::execute, "forbidden token: TODO");
	}

	@Test
	void ignoresAForbiddenTokenInsideACodeFence() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n```\nTODO inside code\n```\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		assertDoesNotThrow(rule::execute);
	}

	/** Commented-out text is inert, so a token an author retired no longer trips the ban. */
	@Test
	void ignoresAForbiddenTokenInsideAnHtmlComment() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n<!--\nTODO: finish this.\n-->\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		assertDoesNotThrow(rule::execute);
	}

	/** The same reading the other way round: a commented-out mention satisfies nothing. */
	@Test
	void failsWhenTheAgentsReferenceAppearsOnlyInsideAnHtmlComment() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace(
				"See [AGENTS.md](AGENTS.md) for the full agent guide.",
				"<!--\nSee [AGENTS.md](AGENTS.md) for the full agent guide.\n-->"));

		assertFailure(EnforcerRuleException.class, rule::execute, "must reference AGENTS.md");
	}

	/** A link an author commented out is one the document no longer makes. */
	@Test
	void ignoresAFileReferenceInsideAnHtmlComment() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md\n");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n<!--\nWas: [gone](docs/absent.md)\n-->\n");
		rule.setValidateFileReferences(true);

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

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required section heading: ## Testing");
	}

	@Test
	void failsWhenSectionHeadingAppearsOnlyInsideAnIndentedBlock() {
		// Indenting four columns is the other way Markdown quotes a sample, so a
		// heading shown as an example must no more satisfy the requirement than one
		// inside a fence does.
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing\nWrite unit tests for all new logic.\n",
				"For example:\n\n    ## Testing\n\n    Write unit tests for all new logic.\n"));

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required section heading: ## Testing");
	}

	@Test
	void treatsAForbiddenTokenInsideAnIndentedBlockAsSampleCode() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nFor example:\n\n    TODO in a sample\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void treatsALinkInsideAnIndentedBlockAsSampleCode() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nFor example:\n\n    See [guide](example.md).\n");
		rule.setValidateFileReferences(true);

		// The sample's target names no file this document links to, so requiring it to
		// exist failed the build over a file the author never meant to ship.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void treatsABacktickLineInsideATildeFenceAsCode() {
		// The ``` does not close the ~~~ block, so the ## Testing heading between the
		// two backtick lines stays code and the required section is reported missing.
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing", "~~~\n```\n## Testing\n```\n~~~"));

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required section heading: ## Testing");
	}

	@Test
	void treatsAnAnnotatedBacktickLineInsideABacktickFenceAsCode() {
		// ```java is an opening delimiter, not a closing one. Ending the block on it
		// would leave the real ``` to open a fresh fence that swallows the rest of
		// the document, so every heading below would be reported missing.
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace("## Testing",
				"```\nexample:\n```java\nint x;\n```\n\n## Testing"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void treatsAShorterFenceInsideALongerOneAsCode() {
		// The four-backtick wrapper is how a document shows a fenced example, so the
		// inner three-backtick delimiters and everything they wrap stay code.
		ClaudeMdFormatRule rule = ruleFor(
				VALID_CONTENT + "\n````markdown\n```java\nTODO in an example\n```\n````\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void scansTheContentThatFollowsANestedFence() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n```\n```java\n```\n\nTODO left behind\n");
		rule.setForbiddenTokens(java.util.List.of("TODO"));

		assertFailure(EnforcerRuleException.class, rule::execute, "forbidden token: TODO");
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

		assertFailure(EnforcerRuleException.class, rule::execute, "out of order");
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

		assertFailure(EnforcerRuleException.class, rule::execute, "exceeds 120 characters");
	}

	@Test
	void failsWhenAFileReferenceIsMissing() {
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT);
		rule.setValidateFileReferences(true);

		assertFailure(EnforcerRuleException.class, rule::execute, "missing file: AGENTS.md");
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
	void resolvesAReferenceWhosePathCarriesParentheses() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		writeString(tempDir.resolve("assets/logo(1).png"), "binary");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nSee ![logo](assets/logo(1).png).\n");
		rule.setValidateFileReferences(true);

		// Stopping at the first closing parenthesis truncated the target and reported
		// the half of it that naturally does not exist.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void stillReportsTheSecondLinkOnALineAsMissing() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\n[a](AGENTS.md) and [b](absent.md).\n");
		rule.setValidateFileReferences(true);

		assertFailure(EnforcerRuleException.class, rule::execute, "missing file: absent.md");
	}

	@Test
	void resolvesAReferenceWhosePathIsPercentEncoded() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		writeString(tempDir.resolve("docs/my guide.md"), "# Guide");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nSee [guide](docs/my%20guide.md).\n");
		rule.setValidateFileReferences(true);

		// A Markdown destination escapes the characters a URL cannot carry raw, so a
		// link to a real file with a space in its name is written with %20 and must
		// not be reported as missing.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void resolvesAReferenceWhosePathReallyContainsAPercentSign() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		writeString(tempDir.resolve("docs/100%25.md"), "# Percent");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nSee [pct](docs/100%25.md).\n");
		rule.setValidateFileReferences(true);

		// Both readings are accepted, so decoding never hides a file whose name
		// genuinely carries the escape.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void stillReportsAPercentEncodedReferenceThatResolvesToNothing() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nSee [guide](docs/no%20such.md).\n");
		rule.setValidateFileReferences(true);

		assertFailure(EnforcerRuleException.class, rule::execute, "missing file: docs/no%20such.md");
	}

	@Test
	void ignoresExternalLinksWhenValidatingReferences() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT + "\nSee [site](https://example.com) and [top](#project).\n");
		rule.setValidateFileReferences(true);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void doesNotReportASectionEmptyWhenItsBodyOpensWithHashPrefixedProse() {
		ClaudeMdFormatRule rule = ruleFor(
				VALID_CONTENT.replace("## Maven\nVersions live in the root pom.", "## Maven\n#1 rule: run mvn install."));

		// "#1 rule:" is prose, not a heading ending the section, so the Maven section
		// has a body.
		assertDoesNotThrow(rule::execute);
	}

	@Test
	void ignoresALinkQuotedAsCodeWhenValidatingReferences() {
		writeString(tempDir.resolve("AGENTS.md"), "# AGENTS.md");
		ClaudeMdFormatRule rule = ruleFor(
				VALID_CONTENT + "\nWrite a link as `[label](example.md)` in your docs.\n");
		rule.setValidateFileReferences(true);

		// Documentation about Markdown quotes a sample link as code precisely because
		// it is an example, so its target is not a reference to resolve on disk.
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

	@Test
	void failsWhenSectionHeadingIsCommentedOut() {
		// A section removed with an HTML comment is gone from the document, so the
		// heading inside it must not satisfy the requirement it was written for.
		ClaudeMdFormatRule rule = ruleFor(VALID_CONTENT.replace(
				"## Testing\nWrite unit tests for all new logic.\n",
				"<!--\n## Testing\nWrite unit tests for all new logic.\n-->\n"));

		assertFailure(EnforcerRuleException.class, rule::execute, "missing required section heading: ## Testing");
	}
}

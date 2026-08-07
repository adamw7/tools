package io.github.adamw7.tools.enforcer.okf;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.createDirectory;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class OkfBundleFormatRuleTest {

	private static final String ROOT_INDEX = """
			---
			okf_version: "0.2"
			---

			# Files

			* [A.java](A.java.md) - Java source file with no project dependencies.
			""";

	private static final String CONCEPT = """
			---
			type: "Java Source File"
			title: "A.java"
			description: "Java source file with no project dependencies."
			generated: { by: "tools.code.context/1", at: "2026-08-03T10:15:30Z" }
			---

			# Dependencies

			This file depends on no other file in the project.
			""";

	@TempDir
	private Path tempDir;

	private Path bundle;

	@BeforeEach
	void setUp() {
		bundle = createDirectory(tempDir.resolve("okf"));
	}

	@Test
	void passesForAConformantBundle() {
		writeString(bundle.resolve("index.md"), ROOT_INDEX);
		writeString(bundle.resolve("A.java.md"), CONCEPT);

		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void passesWhenTheBundleIsAbsentSoTheRuleCanBeWiredBeforeOneExists() {
		OkfBundleFormatRule rule = new OkfBundleFormatRule();
		rule.setBundleDir(tempDir.resolve("no-bundle-here").toFile());

		assertDoesNotThrow(rule::execute);
	}

	/**
	 * An absent bundle is the ordinary state of a rule wired before a repository
	 * emits one, and returning early on it skipped the reporting the pass goes
	 * through — leaving a previous run's failing HTML report on disk, claiming a
	 * failure for a check that had just passed.
	 */
	@Test
	void anAbsentBundleRefreshesTheReportRatherThanLeavingTheLastFailure() throws IOException {
		File report = tempDir.resolve("report.html").toFile();
		OkfBundleFormatRule failing = rule();
		failing.setReportFile(report);
		writeString(bundle.resolve("A.java.md"), "# Dependencies\n");
		assertThrows(EnforcerRuleException.class, failing::execute);

		Files.delete(bundle.resolve("A.java.md"));
		Files.delete(bundle);
		OkfBundleFormatRule absent = rule();
		absent.setReportFile(report);

		assertDoesNotThrow(absent::execute);
		String html = Files.readString(report.toPath());
		assertTrue(html.contains("Check passed"), html);
		assertFalse(html.contains("Check failed"), html);
	}

	@Test
	void passesForAnEmptyBundleDirectory() {
		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void failsWhenNotConfigured() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				new OkfBundleFormatRule()::execute);
		assertTrue(exception.getMessage().contains("bundleDir parameter is not configured"), exception.getMessage());
	}

	@Test
	void failsWhenAConceptHasNoFrontmatter() {
		writeString(bundle.resolve("A.java.md"), "# Dependencies\n\nNone.\n");

		assertTrue(failure().contains("A.java.md has no parseable YAML frontmatter block"));
	}

	@Test
	void failsWhenAFrontmatterBlockIsNeverClosed() {
		writeString(bundle.resolve("A.java.md"), "---\ntype: \"Java Source File\"\n\n# Dependencies\n");

		assertTrue(failure().contains("A.java.md has no parseable YAML frontmatter block"));
	}

	@Test
	void failsWhenAConceptDeclaresNoType() {
		writeString(bundle.resolve("A.java.md"), "---\ntitle: \"A.java\"\n---\n\n# Dependencies\n");

		assertTrue(failure().contains("declares no non-empty 'type', the one mandatory OKF field"));
	}

	@Test
	void failsWhenTheTypeIsEmpty() {
		writeString(bundle.resolve("A.java.md"), "---\ntype: \n---\n\n# Dependencies\n");

		assertTrue(failure().contains("declares no non-empty 'type'"));
	}

	@Test
	void failsWhenAKeyIsDeclaredTwice() {
		writeString(bundle.resolve("A.java.md"),
				"---\ntype: \"Java Source File\"\ntype: \"Project File\"\n---\n\n# Dependencies\n");

		assertTrue(failure().contains("A.java.md declares 'type' more than once"));
	}

	@Test
	void toleratesAnUnknownTypeBecauseOkfRegistersNone() {
		writeString(bundle.resolve("Recipe.md"), "---\ntype: \"Something Nobody Registered\"\n---\n\n# Body\n");

		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void failsWhenAStatusIsOutsideTheSpecifiedVocabulary() {
		writeString(bundle.resolve("A.java.md"),
				"---\ntype: \"Java Source File\"\nstatus: \"provisional\"\n---\n\n# Dependencies\n");

		assertTrue(failure().contains("declares an unknown 'status': provisional"));
	}

	@Test
	void acceptsEveryStatusTheSpecificationDefines() {
		writeString(bundle.resolve("a.md"), "---\ntype: \"T\"\nstatus: draft\n---\n\n# Body\n");
		writeString(bundle.resolve("b.md"), "---\ntype: \"T\"\nstatus: stable\n---\n\n# Body\n");
		writeString(bundle.resolve("c.md"), "---\ntype: \"T\"\nstatus: deprecated\n---\n\n# Body\n");

		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void failsWhenStaleAfterIsNotAnIsoDate() {
		writeString(bundle.resolve("A.java.md"),
				"---\ntype: \"Java Source File\"\nstale_after: \"next quarter\"\n---\n\n# Dependencies\n");

		assertTrue(failure().contains("declares a 'stale_after' that is not an ISO 8601 date: next quarter"));
	}

	@Test
	void failsWhenStaleAfterIsNotARealCalendarDate() {
		writeString(bundle.resolve("A.java.md"),
				"---\ntype: \"Java Source File\"\nstale_after: 2026-13-45\n---\n\n# Dependencies\n");

		assertTrue(failure().contains("not an ISO 8601 date: 2026-13-45"));
	}

	@Test
	void failsWhenGeneratedNamesNoActor() {
		writeString(bundle.resolve("A.java.md"),
				"---\ntype: \"Java Source File\"\ngenerated: { at: 2026-08-03T10:15:30Z }\n---\n\n# Dependencies\n");

		assertTrue(failure().contains("declares a 'generated' without the actor that produced it"));
	}

	/**
	 * YAML writes a mapping in flow or block style, and OKF prefers neither. Reading
	 * only the flow spelling failed a conformant concept for not naming the actor
	 * standing on the very next line.
	 */
	@Test
	void acceptsAGeneratedMappingWrittenInBlockStyle() {
		writeString(bundle.resolve("A.java.md"), """
				---
				type: "Java Source File"
				generated:
				  by: "tools.code.context/1"
				  at: "2026-08-03T10:15:30Z"
				---

				# Dependencies
				""");

		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void failsWhenAGeneratedMappingInBlockStyleNamesNoActor() {
		writeString(bundle.resolve("A.java.md"), """
				---
				type: "Java Source File"
				generated:
				  at: "2026-08-03T10:15:30Z"
				---

				# Dependencies
				""");

		assertTrue(failure().contains("declares a 'generated' without the actor that produced it"));
	}

	@Test
	void failsWhenANestedIndexCarriesFrontmatter() {
		Path pkg = createDirectory(bundle.resolve("pkg"));
		writeString(pkg.resolve("index.md"), "---\ntype: \"Listing\"\n---\n\n# Files\n");

		assertTrue(failure().contains("pkg/index.md carries frontmatter"));
	}

	@Test
	void failsWhenTheRootIndexDeclaresMoreThanTheVersion() {
		writeString(bundle.resolve("index.md"), "---\nokf_version: \"0.2\"\ntype: \"Listing\"\n---\n\n# Files\n");

		assertTrue(failure().contains("index.md declares 'type'; a bundle-root index.md may declare only"));
	}

	@Test
	void passesForAnIndexWithoutFrontmatter() {
		writeString(bundle.resolve("index.md"), "# Files\n\n* [A.java](A.java.md) - a file.\n");
		writeString(bundle.resolve("A.java.md"), CONCEPT);

		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void failsWhenALogGroupsEntriesUnderSomethingOtherThanADate() {
		writeString(bundle.resolve("log.md"), "# Update Log\n\n## last week\n\n* **Update**: something.\n");

		assertTrue(failure().contains("log.md groups entries under 'last week', which is not an ISO 8601 date"));
	}

	@Test
	void passesForALogWithIsoDateHeadings() {
		writeString(bundle.resolve("log.md"), "# Update Log\n\n## 2026-05-22\n\n* **Update**: something.\n");

		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void failsWhenTheDeclaredVersionIsNotThePinnedOne() {
		writeString(bundle.resolve("index.md"), "---\nokf_version: \"0.1\"\n---\n\n# Files\n");
		OkfBundleFormatRule rule = rule();
		rule.setOkfVersion("0.2");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("declares 'okf_version: 0.1' but the build expects 0.2"),
				exception.getMessage());
	}

	@Test
	void failsWhenAPinnedVersionHasNoRootIndexToDeclareIt() {
		writeString(bundle.resolve("A.java.md"), CONCEPT);
		OkfBundleFormatRule rule = rule();
		rule.setOkfVersion("0.2");

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("declares no index.md at its root"), exception.getMessage());
	}

	@Test
	void doesNotCheckTheVersionWhenNoneIsPinned() {
		writeString(bundle.resolve("index.md"), "---\nokf_version: \"0.1\"\n---\n\n# Files\n");

		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void failsWhenARequiredKeyIsMissing() {
		writeString(bundle.resolve("A.java.md"), "---\ntype: \"Java Source File\"\n---\n\n# Dependencies\n");
		OkfBundleFormatRule rule = rule();
		rule.setRequiredKeys(List.of("title", "description"));

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("declares no non-empty 'title'"), exception.getMessage());
		assertTrue(exception.getMessage().contains("declares no non-empty 'description'"), exception.getMessage());
	}

	@Test
	void failsWhenADirectoryHoldingConceptsHasNoIndexAndOneIsRequired() {
		Path pkg = createDirectory(bundle.resolve("pkg"));
		writeString(pkg.resolve("A.java.md"), CONCEPT);
		OkfBundleFormatRule rule = rule();
		rule.setRequireIndex(true);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertTrue(exception.getMessage().contains("pkg holds concepts but no index.md"), exception.getMessage());
	}

	@Test
	void aMissingIndexIsToleratedByDefault() {
		writeString(bundle.resolve("A.java.md"), CONCEPT);

		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void reportsEveryProblemTogetherRatherThanTheFirst() {
		writeString(bundle.resolve("A.java.md"), "---\ntitle: \"A.java\"\n---\n\n# Dependencies\n");
		writeString(bundle.resolve("B.java.md"), "# Dependencies\n");

		String message = failure();
		assertTrue(message.contains("A.java.md declares no non-empty 'type'"), message);
		assertTrue(message.contains("B.java.md has no parseable YAML frontmatter block"), message);
	}

	@Test
	void namesTheDocumentRelativeToTheBundleRoot() {
		Path pkg = createDirectory(bundle.resolve("pkg"));
		writeString(pkg.resolve("A.java.md"), "# Dependencies\n");

		assertTrue(failure().contains("pkg/A.java.md has no parseable"));
	}

	@Test
	void ignoresFilesThatAreNotMarkdown() {
		writeString(bundle.resolve("notes.txt"), "no frontmatter here");

		assertDoesNotThrow(rule()::execute);
	}

	@Test
	void warnSeverityLogsInsteadOfFailing() {
		writeString(bundle.resolve("A.java.md"), "# Dependencies\n");
		CapturingLogger logger = new CapturingLogger();
		OkfBundleFormatRule rule = rule();
		rule.setSeverity("warn");
		rule.setLog(logger);

		assertDoesNotThrow(rule::execute);
		assertEquals(1, logger.warnings().size());
		assertTrue(logger.warnings().get(0).contains("has no parseable YAML frontmatter block"));
	}

	private String failure() {
		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule()::execute);
		return exception.getMessage();
	}

	private OkfBundleFormatRule rule() {
		OkfBundleFormatRule rule = new OkfBundleFormatRule();
		rule.setBundleDir(bundle.toFile());
		return rule;
	}
}

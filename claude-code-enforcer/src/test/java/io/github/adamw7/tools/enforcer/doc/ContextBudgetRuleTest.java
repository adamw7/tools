package io.github.adamw7.tools.enforcer.doc;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeBytes;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.rule.CapturingLogger;

class ContextBudgetRuleTest {

	@TempDir
	private Path tempDir;

	@Test
	void passesWhenTheFileFitsTheBudget() {
		ContextBudgetRule rule = ruleForFile("# CLAUDE.md\n\nShort.\n");
		rule.setMaxBytes(1000);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenNoLimitIsConfigured() {
		assertFailure(EnforcerRuleException.class, ruleForFile("content")::execute,
				"maxBytes, maxLines, or maxTokens");
	}

	@Test
	void failsWhenNoTargetsAreConfigured() {
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setMaxBytes(1000);

		assertFailure(EnforcerRuleException.class, rule::execute, "files or directories");
	}

	@Test
	void failsWhenAConfiguredFileIsMissing() {
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setMaxBytes(1000);
		rule.setFiles(List.of(tempDir.resolve("absent.md").toFile()));

		assertFailure(EnforcerRuleException.class, rule::execute, "does not exist");
	}

	@Test
	void failsWhenTheByteBudgetIsExceeded() {
		ContextBudgetRule rule = ruleForFile("x".repeat(100));
		rule.setMaxBytes(50);

		assertFailure(EnforcerRuleException.class, rule::execute, "over the 50-byte budget");
	}

	@Test
	void passesWhenTheFileIsExactlyOnTheByteBudget() {
		// The budget is a maximum, not a ceiling to stay under, so a file measuring
		// exactly maxBytes must pass. Together with the test below this pins which side
		// of the comparison the boundary falls on; a rule that rejected an exact fit,
		// or accepted one byte over, still satisfies the round-number tests.
		ContextBudgetRule rule = ruleForFile("x".repeat(50));
		rule.setMaxBytes(50);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenTheFileIsOneByteOverTheBudget() {
		ContextBudgetRule rule = ruleForFile("x".repeat(51));
		rule.setMaxBytes(50);

		assertFailure(EnforcerRuleException.class, rule::execute, "is 51 bytes, over the 50-byte budget");
	}

	@Test
	void passesWhenTheFileIsExactlyOnTheLineBudget() {
		ContextBudgetRule rule = ruleForFile("one\ntwo\n");
		rule.setMaxLines(2);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenTheFileIsOneLineOverTheBudget() {
		ContextBudgetRule rule = ruleForFile("one\ntwo\nthree\n");
		rule.setMaxLines(2);

		assertFailure(EnforcerRuleException.class, rule::execute, "has 3 lines, over the 2-line budget");
	}

	@Test
	void passesWhenTheFileIsExactlyOnTheTokenBudget() {
		// Tokens round up at four characters each, so 40 characters is exactly 10.
		ContextBudgetRule rule = ruleForFile("x".repeat(40));
		rule.setMaxTokens(10);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void failsWhenTheFileIsOneTokenOverTheBudget() {
		// One character past the exact fit rounds up to the eleventh token.
		ContextBudgetRule rule = ruleForFile("x".repeat(41));
		rule.setMaxTokens(10);

		assertFailure(EnforcerRuleException.class, rule::execute, "estimated 11 tokens, over the 10-token budget");
	}

	@Test
	void failsWhenTheLineBudgetIsExceeded() {
		ContextBudgetRule rule = ruleForFile("one\ntwo\nthree\n");
		rule.setMaxLines(2);

		assertFailure(EnforcerRuleException.class, rule::execute, "over the 2-line budget");
	}

	@Test
	void failsWhenTheTokenBudgetIsExceeded() {
		ContextBudgetRule rule = ruleForFile("x".repeat(100));
		rule.setMaxTokens(10);

		assertFailure(EnforcerRuleException.class, rule::execute, "estimated 25 tokens, over the 10-token budget");
	}

	@Test
	void measuresMarkdownFilesUnderDirectories() {
		writeString(tempDir.resolve("skills/big/SKILL.md"), "x".repeat(100));
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setDirectories(List.of(tempDir.resolve("skills").toFile()));
		rule.setMaxBytes(50);

		assertFailure(EnforcerRuleException.class, rule::execute, "SKILL.md");
	}

	@Test
	void skipsNonMarkdownFilesAndAbsentDirectories() {
		writeString(tempDir.resolve("skills/big/data.json"), "x".repeat(100));
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setDirectories(List.of(tempDir.resolve("skills").toFile(), tempDir.resolve("absent").toFile()));
		rule.setMaxBytes(50);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void passesWhenTheOnlyViolationIsRecordedInTheBaseline() {
		ContextBudgetRule rule = ruleForFile("x".repeat(100));
		rule.setMaxBytes(50);
		File baseline = tempDir.resolve("baseline.txt").toFile();
		rule.setLog(new CapturingLogger());
		rule.setBaselineFile(baseline);
		rule.setWriteBaseline(true);
		assertDoesNotThrow(rule::execute);

		rule.setWriteBaseline(false);

		assertDoesNotThrow(rule::execute);
	}

	@Test
	void stillFailsForAViolationTheBaselineDoesNotCover() {
		File baseline = tempDir.resolve("baseline.txt").toFile();
		writeString(baseline.toPath(), "${basedir}/some-other-file.md is 999 bytes, over the 50-byte budget\n");
		ContextBudgetRule rule = ruleForFile("x".repeat(100));
		rule.setMaxBytes(50);
		rule.setBaselineFile(baseline);
		rule.setLog(new CapturingLogger());

		assertFailure(EnforcerRuleException.class, rule::execute, "over the 50-byte budget");
	}

	@Test
	void writeBaselineModeRecordsTheViolationsAndPasses() {
		ContextBudgetRule rule = ruleForFile("x".repeat(100));
		rule.setMaxBytes(50);
		File baseline = tempDir.resolve("baseline.txt").toFile();
		rule.setBaselineFile(baseline);
		rule.setWriteBaseline(true);
		rule.setLog(new CapturingLogger());

		assertDoesNotThrow(rule::execute);
		assertTrue(baseline.isFile(), "expected the baseline file to be written");
	}

	@Test
	void writeBaselineModeRefreshesTheReportAsThePassItIs() throws IOException {
		ContextBudgetRule rule = ruleForFile("x".repeat(100));
		rule.setMaxBytes(50);
		File report = tempDir.resolve("report.html").toFile();
		rule.setReportFile(report);
		File baseline = tempDir.resolve("baseline.txt").toFile();
		rule.setBaselineFile(baseline);
		rule.setWriteBaseline(true);
		rule.setLog(new CapturingLogger());

		assertDoesNotThrow(rule::execute);
		String html = Files.readString(report.toPath());
		assertTrue(html.contains("Check passed"), html);
		// Every violation was recorded, so none is left un-suppressed to report.
		assertFalse(html.contains("over the 50-byte budget"), html);
		assertTrue(Files.readString(baseline.toPath()).contains("over the 50-byte budget"),
				Files.readString(baseline.toPath()));
	}

	@Test
	void reportsAMarkdownFileThatCannotBeDecodedInsteadOfFailingTheBuildOutright() {
		Path directory = tempDir.resolve("skills");
		writeBytes(directory.resolve("broken.md"), new byte[] { (byte) 0xFF, (byte) 0xFE, (byte) 0x00 });
		writeString(directory.resolve("fine.md"), "one\ntwo\n");
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setDirectories(List.of(directory.toFile()));
		rule.setMaxLines(1);

		EnforcerRuleException exception = assertFailure(EnforcerRuleException.class, rule::execute,
				"cannot be read as text", "broken.md");
		// The undecodable file no longer aborts the run before the next one is measured.
		assertTrue(exception.getMessage().contains("fine.md"), exception.getMessage());
		assertTrue(exception.getMessage().contains("2 lines, over the 1-line budget"), exception.getMessage());
	}

	@Test
	void measuresAFileNamedAndScannedAsADirectoryEntryOnlyOnce() {
		Path directory = tempDir.resolve("skills");
		Path file = writeString(directory.resolve("SKILL.md"), "x".repeat(100));
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setFiles(List.of(file.toFile()));
		rule.setDirectories(List.of(directory.toFile()));
		rule.setMaxBytes(10);

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);
		assertEquals(1, occurrences(exception.getMessage(), "over the 10-byte budget"), exception.getMessage());
	}

	@Test
	void stillMeasuresAConfiguredFileThatIsNotMarkdown() {
		Path file = writeString(tempDir.resolve("notes.txt"), "x".repeat(100));
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setFiles(List.of(file.toFile()));
		rule.setDirectories(List.of(tempDir.toFile()));
		rule.setMaxBytes(10);

		// The *.md filter narrows the directory scan; a file named outright was
		// chosen by the configuration and is measured whatever it is called.
		assertFailure(EnforcerRuleException.class, rule::execute, "notes.txt");
	}

	private static int occurrences(String message, String token) {
		return message.split(java.util.regex.Pattern.quote(token), -1).length - 1;
	}

	private ContextBudgetRule ruleForFile(String content) {
		Path file = tempDir.resolve("CLAUDE.md");
		writeString(file, content);
		ContextBudgetRule rule = new ContextBudgetRule();
		rule.setFiles(List.of(file.toFile()));
		return rule;
	}
}

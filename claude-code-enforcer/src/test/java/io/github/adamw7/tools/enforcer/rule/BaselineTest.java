package io.github.adamw7.tools.enforcer.rule;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.readString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineTest {

	@TempDir
	private Path tempDir;

	@Test
	void aNullFileSuppressesNothing() {
		List<String> violations = List.of("a", "b");

		assertEquals(violations, assertDoesNotThrow(() -> Baseline.read(null, projectDir()).newViolations(violations)));
	}

	@Test
	void anAbsentFileSuppressesNothing() {
		File absent = tempDir.resolve("absent.txt").toFile();
		List<String> violations = List.of("a", "b");

		assertEquals(violations, assertDoesNotThrow(() -> Baseline.read(absent, projectDir()).newViolations(violations)));
	}

	@Test
	void recordedViolationsAreSuppressedAndNewOnesRemainInOrder() {
		File file = writeString("known one\nknown two\n");

		List<String> newViolations = assertDoesNotThrow(() -> Baseline.read(file, projectDir())
				.newViolations(List.of("known one", "fresh", "known two", "another fresh")));

		assertEquals(List.of("fresh", "another fresh"), newViolations);
	}

	@Test
	void blankLinesAndCommentsInTheBaselineAreIgnored() {
		File file = writeString("# a comment\n\nknown one\n   \n# another\n");

		List<String> newViolations = assertDoesNotThrow(() -> Baseline.read(file, projectDir())
				.newViolations(List.of("known one", "# a comment", "fresh")));

		assertEquals(List.of("# a comment", "fresh"), newViolations);
	}

	@Test
	void aRecordedBaselineSuppressesTheSameViolationsOnReadBack() {
		File file = tempDir.resolve("baseline.txt").toFile();
		List<String> violations = List.of(tempDir + "/CLAUDE.md is over budget", "AGENTS.md drifted");

		assertDoesNotThrow(() -> Baseline.write(file, violations, projectDir()));

		assertTrue(assertDoesNotThrow(() -> Baseline.read(file, projectDir()).newViolations(violations)).isEmpty());
	}

	@Test
	void aWrittenBaselineNormalisesTheProjectBaseDirectorySoItIsPortable() {
		File file = tempDir.resolve("baseline.txt").toFile();
		String base = projectDir().toString();

		assertDoesNotThrow(() -> Baseline.write(file, List.of(base + "/CLAUDE.md is over budget"), projectDir()));

		String content = readString(file);
		assertTrue(content.contains("${basedir}/CLAUDE.md is over budget"), content);
		assertFalse(content.contains(base + "/CLAUDE.md"), content);
	}

	@Test
	void aTokenisedEntryMatchesALiveViolationCarryingTheAbsolutePath() {
		File file = writeString("${basedir}/CLAUDE.md is over budget\n");
		String base = projectDir().toString();

		List<String> newViolations = assertDoesNotThrow(() -> Baseline.read(file, projectDir())
				.newViolations(List.of(base + "/CLAUDE.md is over budget")));

		assertTrue(newViolations.isEmpty(), newViolations.toString());
	}

	@Test
	void aWrittenBaselineIsSortedDeduplicatedAndCarriesAHeader() {
		File file = tempDir.resolve("baseline.txt").toFile();

		assertDoesNotThrow(() -> Baseline.write(file, List.of("b violation", "a violation", "b violation"), projectDir()));

		assertTrue(readString(file).startsWith("#"), "expected a leading comment header");
		assertEquals(List.of("a violation", "b violation"), filteredLines(file));
	}

	@Test
	void staleEntriesAreTheRecordedViolationsNoCurrentViolationMatches() {
		File file = writeString("still failing\nfixed since\n");

		List<String> stale = assertDoesNotThrow(() -> Baseline.read(file, projectDir())
				.staleEntries(List.of("still failing", "brand new")));

		assertEquals(List.of("fixed since"), stale);
	}

	@Test
	void theConfiguredBaseDirectoryIsTokenisedRatherThanTheWorkingDirectory() {
		File file = tempDir.resolve("baseline.txt").toFile();
		String workingDir = Path.of("").toAbsolutePath().toString();

		assertDoesNotThrow(() -> Baseline.write(file, List.of(projectDir() + "/CLAUDE.md is over budget",
				workingDir + "/elsewhere.md is over budget"), projectDir()));

		String content = readString(file);
		assertTrue(content.contains("${basedir}/CLAUDE.md is over budget"), content);
		assertFalse(content.contains(projectDir() + "/CLAUDE.md"), content);
		// The working directory is not the project, so it is left as it was written.
		assertTrue(content.contains(workingDir + "/elsewhere.md is over budget"), content);
	}

	@Test
	void aBaselineRecordedInOneProjectDirectoryStillSuppressesWhenReadFromAnother() {
		File file = writeString("${basedir}/CLAUDE.md is over budget\n");
		File otherCheckout = tempDir.resolve("elsewhere/checkout").toFile();
		String outsideTheProject = tempDir.resolve("unrelated").toFile() + "/CLAUDE.md is over budget";

		List<String> newViolations = assertDoesNotThrow(() -> Baseline.read(file, otherCheckout)
				.newViolations(List.of(otherCheckout + "/CLAUDE.md is over budget", outsideTheProject)));

		// The token matches only what sits under the configured project directory.
		assertEquals(List.of(outsideTheProject), newViolations);
	}

	@Test
	void anUnconfiguredBaseDirectoryFallsBackToTheWorkingDirectory() {
		File file = tempDir.resolve("baseline.txt").toFile();
		String workingDir = Path.of("").toAbsolutePath().toString();

		assertDoesNotThrow(() -> Baseline.write(file, List.of(workingDir + "/CLAUDE.md is over budget"), null));

		assertTrue(readString(file).contains("${basedir}/CLAUDE.md is over budget"), readString(file));
	}

	/** The project base directory a rule would be configured with, as {@code ${project.basedir}}. */
	private File projectDir() {
		return tempDir.toFile();
	}

	private static List<String> filteredLines(File file) {
		return readString(file).lines().filter(line -> !line.startsWith("#")).toList();
	}

	private File writeString(String content) {
		return TestFiles.writeString(tempDir.resolve("baseline.txt"), content).toFile();
	}

}

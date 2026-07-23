package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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

		assertEquals(violations, assertDoesNotThrow(() -> Baseline.read(null).newViolations(violations)));
	}

	@Test
	void anAbsentFileSuppressesNothing() {
		File absent = tempDir.resolve("absent.txt").toFile();
		List<String> violations = List.of("a", "b");

		assertEquals(violations, assertDoesNotThrow(() -> Baseline.read(absent).newViolations(violations)));
	}

	@Test
	void recordedViolationsAreSuppressedAndNewOnesRemainInOrder() {
		File file = writeString("known one\nknown two\n");

		List<String> newViolations = assertDoesNotThrow(() -> Baseline.read(file)
				.newViolations(List.of("known one", "fresh", "known two", "another fresh")));

		assertEquals(List.of("fresh", "another fresh"), newViolations);
	}

	@Test
	void blankLinesAndCommentsInTheBaselineAreIgnored() {
		File file = writeString("# a comment\n\nknown one\n   \n# another\n");

		List<String> newViolations = assertDoesNotThrow(() -> Baseline.read(file)
				.newViolations(List.of("known one", "# a comment", "fresh")));

		assertEquals(List.of("# a comment", "fresh"), newViolations);
	}

	@Test
	void aRecordedBaselineSuppressesTheSameViolationsOnReadBack() {
		File file = tempDir.resolve("baseline.txt").toFile();
		List<String> violations = List.of(tempDir + "/CLAUDE.md is over budget", "AGENTS.md drifted");

		assertDoesNotThrow(() -> Baseline.write(file, violations));

		assertTrue(assertDoesNotThrow(() -> Baseline.read(file).newViolations(violations)).isEmpty());
	}

	@Test
	void aWrittenBaselineNormalisesTheProjectBaseDirectorySoItIsPortable() {
		File file = tempDir.resolve("baseline.txt").toFile();
		String base = Path.of("").toAbsolutePath().toString();

		assertDoesNotThrow(() -> Baseline.write(file, List.of(base + "/CLAUDE.md is over budget")));

		String content = readString(file);
		assertTrue(content.contains("${basedir}/CLAUDE.md is over budget"), content);
		assertFalse(content.contains(base + "/CLAUDE.md"), content);
	}

	@Test
	void aTokenisedEntryMatchesALiveViolationCarryingTheAbsolutePath() {
		File file = writeString("${basedir}/CLAUDE.md is over budget\n");
		String base = Path.of("").toAbsolutePath().toString();

		List<String> newViolations = assertDoesNotThrow(() -> Baseline.read(file)
				.newViolations(List.of(base + "/CLAUDE.md is over budget")));

		assertTrue(newViolations.isEmpty(), newViolations.toString());
	}

	@Test
	void aWrittenBaselineIsSortedDeduplicatedAndCarriesAHeader() {
		File file = tempDir.resolve("baseline.txt").toFile();

		assertDoesNotThrow(() -> Baseline.write(file, List.of("b violation", "a violation", "b violation")));

		assertTrue(readString(file).startsWith("#"), "expected a leading comment header");
		assertEquals(List.of("a violation", "b violation"), filteredLines(file));
	}

	@Test
	void staleEntriesAreTheRecordedViolationsNoCurrentViolationMatches() {
		File file = writeString("still failing\nfixed since\n");

		List<String> stale = assertDoesNotThrow(() -> Baseline.read(file)
				.staleEntries(List.of("still failing", "brand new")));

		assertEquals(List.of("fixed since"), stale);
	}

	private static List<String> filteredLines(File file) {
		return readString(file).lines().filter(line -> !line.startsWith("#")).toList();
	}

	private File writeString(String content) {
		Path file = tempDir.resolve("baseline.txt");
		try {
			return Files.writeString(file, content).toFile();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}

	private static String readString(File file) {
		try {
			return Files.readString(file.toPath());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + file, e);
		}
	}
}

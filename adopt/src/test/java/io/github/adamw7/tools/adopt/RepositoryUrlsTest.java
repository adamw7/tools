package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryUrlsTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String OTHER_URL = "https://github.com/owner/other.git";

	@Test
	void readsOneRepositoryPerLine(@TempDir Path dir) throws IOException {
		assertEquals(List.of(REPO_URL, OTHER_URL), RepositoryUrls.fromFile(listFile(dir, REPO_URL + "\n" + OTHER_URL)));
	}

	/**
	 * A list file is maintained by hand, so it carries the notes and the commented-out
	 * repository a hand-maintained file always grows.
	 */
	@Test
	void skipsBlankLinesAndComments(@TempDir Path dir) throws IOException {
		String content = "# the team's repositories\n\n" + REPO_URL + "\n   \n  # " + OTHER_URL + "\n";
		assertEquals(List.of(REPO_URL), RepositoryUrls.fromFile(listFile(dir, content)));
	}

	@Test
	void stripsSurroundingWhitespace(@TempDir Path dir) throws IOException {
		assertEquals(List.of(REPO_URL), RepositoryUrls.fromFile(listFile(dir, "  " + REPO_URL + "  \n")));
	}

	@Test
	void dropsDuplicatesKeepingTheOrderTheyWereGivenIn() {
		assertEquals(List.of(REPO_URL, OTHER_URL),
				RepositoryUrls.distinct(List.of(REPO_URL, OTHER_URL, " " + REPO_URL + " ")));
	}

	@Test
	void dropsBlankEntries() {
		assertEquals(List.of(REPO_URL), RepositoryUrls.distinct(List.of("  ", REPO_URL, "")));
	}

	@Test
	void readsAnEmptyFileAsNoRepositories(@TempDir Path dir) throws IOException {
		assertTrue(RepositoryUrls.fromFile(listFile(dir, "\n# nothing here\n")).isEmpty());
	}

	@Test
	void failsWithAdoptionExceptionWhenTheFileCannotBeRead(@TempDir Path dir) {
		Path missing = dir.resolve("absent.txt");
		AdoptionException thrown = assertThrows(AdoptionException.class, () -> RepositoryUrls.fromFile(missing));
		assertTrue(thrown.getMessage().contains("the repository URL list"), thrown.getMessage());
	}

	private Path listFile(Path dir, String content) throws IOException {
		return Files.writeString(dir.resolve("repos.txt"), content);
	}
}

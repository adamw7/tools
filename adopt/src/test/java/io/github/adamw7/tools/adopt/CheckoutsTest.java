package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class CheckoutsTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String OTHER_URL = "https://github.com/owner/other.git";
	private static final String BRANCH = "feature/x";
	private static final Path WORKSPACE = Path.of("/tmp/workspace");

	@Test
	void buildsOneContextPerRepositoryInOrder() {
		List<AdoptionContext> contexts = Checkouts.forRun(List.of(REPO_URL, OTHER_URL), WORKSPACE, BRANCH);
		assertEquals(List.of(REPO_URL, OTHER_URL), contexts.stream().map(AdoptionContext::repositoryUrl).toList());
	}

	@Test
	void adoptsEveryRepositoryIntoOneWorkspaceOnOneBranch() {
		List<AdoptionContext> contexts = Checkouts.forRun(List.of(REPO_URL, OTHER_URL), WORKSPACE, BRANCH);
		assertEquals(List.of(WORKSPACE, WORKSPACE), contexts.stream().map(AdoptionContext::workspace).toList());
		assertEquals(List.of(BRANCH, BRANCH), contexts.stream().map(AdoptionContext::branchName).toList());
	}

	@Test
	void givesEachRepositoryItsOwnCheckoutDirectory() {
		List<AdoptionContext> contexts = Checkouts.forRun(List.of(REPO_URL, OTHER_URL), WORKSPACE, BRANCH);
		assertEquals(List.of(WORKSPACE.resolve("repo"), WORKSPACE.resolve("other")),
				contexts.stream().map(AdoptionContext::repositoryDirectory).toList());
	}

	/**
	 * A checkout is named after the repository alone, so two owners' repositories of
	 * the same name claim one directory. The second adoption would reuse the first's
	 * checkout — the clone step skips a checkout that exists — and report a pull
	 * request the second repository never got.
	 */
	@Test
	void rejectsTwoOwnersRepositoriesOfTheSameName() {
		List<String> urls = List.of("https://github.com/owner/tools.git", "https://github.com/other-owner/tools.git");
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> Checkouts.forRun(urls, WORKSPACE, BRANCH));
		assertTrue(exception.getMessage().contains("other-owner/tools"), exception.getMessage());
		assertTrue(exception.getMessage().contains(WORKSPACE.resolve("tools").toString()), exception.getMessage());
	}

	/**
	 * The two clone URLs of one repository are not duplicates as text, so the list
	 * input keeps both — and they name the same checkout, which is what makes them one
	 * adoption rather than two.
	 */
	@Test
	void rejectsTheSameRepositoryNamedWithAndWithoutTheGitSuffix() {
		List<String> urls = List.of("https://github.com/owner/repo", REPO_URL);
		assertThrows(IllegalArgumentException.class, () -> Checkouts.forRun(urls, WORKSPACE, BRANCH));
	}

	@Test
	void rejectsTheSameRepositoryNamedWithAndWithoutATrailingSlash() {
		List<String> urls = List.of("https://github.com/owner/repo/", "https://github.com/owner/repo");
		assertThrows(IllegalArgumentException.class, () -> Checkouts.forRun(urls, WORKSPACE, BRANCH));
	}

	/** The collision is named while both repositories are still untouched. */
	@Test
	void namesBothCollidingRepositories() {
		List<String> urls = List.of(REPO_URL, "https://github.com/other-owner/repo.git");
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> Checkouts.forRun(urls, WORKSPACE, BRANCH));
		assertTrue(exception.getMessage().contains(REPO_URL), exception.getMessage());
		assertTrue(exception.getMessage().contains("other-owner/repo.git"), exception.getMessage());
	}

	@Test
	void adoptsASingleRepository() {
		List<AdoptionContext> contexts = Checkouts.forRun(List.of(REPO_URL), WORKSPACE, BRANCH);
		assertEquals(1, contexts.size());
		assertEquals(WORKSPACE.resolve("repo"), contexts.get(0).repositoryDirectory());
	}

	@Test
	void buildsNoContextsForNoRepositories() {
		assertTrue(Checkouts.forRun(List.of(), WORKSPACE, BRANCH).isEmpty());
	}
}

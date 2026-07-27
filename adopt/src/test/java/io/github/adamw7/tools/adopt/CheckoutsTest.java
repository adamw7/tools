package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class CheckoutsTest {

	private static final String REPO_URL = "https://github.com/owner/repo.git";
	private static final String OTHER_URL = "https://github.com/owner/other.git";
	private static final String BRANCH = "feature/x";
	private static final Path WORKSPACE = Path.of("/tmp/workspace");

	private final Checkouts checkouts = new Checkouts(WORKSPACE, BRANCH);

	@Test
	void buildsOneContextPerRepositoryInOrder() {
		assertEquals(List.of(REPO_URL, OTHER_URL),
				claim(REPO_URL, OTHER_URL).stream().map(AdoptionContext::repositoryUrl).toList());
	}

	@Test
	void adoptsEveryRepositoryIntoOneWorkspaceOnOneBranch() {
		List<AdoptionContext> contexts = claim(REPO_URL, OTHER_URL);
		assertEquals(List.of(WORKSPACE, WORKSPACE), contexts.stream().map(AdoptionContext::workspace).toList());
		assertEquals(List.of(BRANCH, BRANCH), contexts.stream().map(AdoptionContext::branchName).toList());
	}

	@Test
	void namesTheBranchEveryRepositoryOfTheRunIsAdoptedOn() {
		assertEquals(BRANCH, checkouts.branchName());
	}

	@Test
	void givesEachRepositoryItsOwnCheckoutDirectory() {
		assertEquals(List.of(WORKSPACE.resolve("repo"), WORKSPACE.resolve("other")),
				claim(REPO_URL, OTHER_URL).stream().map(AdoptionContext::repositoryDirectory).toList());
	}

	/**
	 * A checkout is named after the repository alone, so two owners' repositories of
	 * the same name claim one directory. The second adoption would reuse the first's
	 * checkout — the clone step skips a checkout that exists — and report a pull
	 * request the second repository never got.
	 */
	@Test
	void rejectsTwoOwnersRepositoriesOfTheSameName() {
		checkouts.claim("https://github.com/owner/tools.git");
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> checkouts.claim("https://github.com/other-owner/tools.git"));
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
		checkouts.claim("https://github.com/owner/repo");
		assertThrows(IllegalArgumentException.class, () -> checkouts.claim(REPO_URL));
	}

	@Test
	void rejectsTheSameRepositoryNamedWithAndWithoutATrailingSlash() {
		checkouts.claim("https://github.com/owner/repo/");
		assertThrows(IllegalArgumentException.class, () -> checkouts.claim("https://github.com/owner/repo"));
	}

	/** The collision names both repositories, since either could be the one meant. */
	@Test
	void namesBothCollidingRepositories() {
		checkouts.claim(REPO_URL);
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> checkouts.claim("https://github.com/other-owner/repo.git"));
		assertTrue(exception.getMessage().contains(REPO_URL), exception.getMessage());
		assertTrue(exception.getMessage().contains("other-owner/repo.git"), exception.getMessage());
	}

	/** A collision names the repository it refused, never the credentials its URL carried. */
	@Test
	void masksTheCredentialsOfACollidingUrl() {
		checkouts.claim(REPO_URL);
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> checkouts.claim("https://x-access-token:secret@github.com/other-owner/repo.git"));
		assertTrue(exception.getMessage().contains("https://***@github.com/other-owner/repo.git"),
				exception.getMessage());
		assertTrue(!exception.getMessage().contains("secret"), exception.getMessage());
	}

	/**
	 * A repository the run cannot place is that one repository's failure, so the ones
	 * behind it still claim their own checkouts.
	 */
	@Test
	void aRefusedClaimLeavesTheRestOfTheRunClaimable() {
		checkouts.claim(REPO_URL);
		assertThrows(IllegalArgumentException.class, () -> checkouts.claim("https://github.com/other-owner/repo.git"));
		assertEquals(WORKSPACE.resolve("other"), checkouts.claim(OTHER_URL).repositoryDirectory());
	}

	@Test
	void rejectsAUrlThatNamesNoRepository() {
		assertThrows(IllegalArgumentException.class, () -> checkouts.claim("https://github.com/owner/.git"));
	}

	/** A refused URL never claims a checkout, so it cannot block the repository that owns it. */
	@Test
	void aUrlThatNamesNoRepositoryClaimsNothing() {
		assertThrows(IllegalArgumentException.class, () -> checkouts.claim("   "));
		assertEquals(WORKSPACE.resolve("repo"), checkouts.claim(REPO_URL).repositoryDirectory());
	}

	@Test
	void adoptsASingleRepository() {
		assertEquals(WORKSPACE.resolve("repo"), checkouts.claim(REPO_URL).repositoryDirectory());
	}

	private List<AdoptionContext> claim(String... repositoryUrls) {
		return Stream.of(repositoryUrls).map(checkouts::claim).toList();
	}
}

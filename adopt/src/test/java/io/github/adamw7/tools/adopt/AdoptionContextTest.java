package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdoptionContextTest {

	private static final String REPOSITORY_URL = "https://github.com/adamw7/tools.git";

	private final Path workspace = Path.of("/tmp/workspace");

	/** The URL itself is parsed by {@link RepositoryUrl}; the context resolves the checkout under the workspace. */
	@Test
	void resolvesTheCheckoutDirectoryUnderTheWorkspace() {
		AdoptionContext context = new AdoptionContext(REPOSITORY_URL, workspace);
		assertEquals(workspace.resolve("tools"), context.repositoryDirectory());
	}

	@Test
	void exposesTheUrlAndItsSlug() {
		AdoptionContext context = new AdoptionContext(REPOSITORY_URL, workspace);
		assertEquals(REPOSITORY_URL, context.repositoryUrl());
		assertEquals("adamw7/tools", context.repositorySlug().orElseThrow());
	}

	@Test
	void exposesTheWorkspaceDirectory() {
		assertEquals(workspace, new AdoptionContext(REPOSITORY_URL, workspace).workspace());
	}

	@Test
	void defaultsToTheAdoptionFeatureBranch() {
		AdoptionContext context = new AdoptionContext(REPOSITORY_URL, workspace);
		assertEquals(AdoptionContext.DEFAULT_BRANCH, context.branchName());
	}

	@Test
	void keepsAndTrimsSuppliedBranchName() {
		AdoptionContext context = new AdoptionContext(REPOSITORY_URL, workspace, "  feature/x  ");
		assertEquals("feature/x", context.branchName());
	}

	@Test
	void rejectsBlankBranchName() {
		assertThrows(IllegalArgumentException.class, () -> new AdoptionContext(REPOSITORY_URL, workspace, "  "));
	}

	@Test
	void rejectsNullBranchName() {
		assertThrows(IllegalArgumentException.class, () -> new AdoptionContext(REPOSITORY_URL, workspace, null));
	}

	@Test
	void rejectsAUrlThatNamesNoRepository() {
		assertThrows(IllegalArgumentException.class, () -> new AdoptionContext("  ", workspace));
		assertThrows(IllegalArgumentException.class, () -> new AdoptionContext(null, workspace));
	}

	@Test
	void rejectsNullWorkspace() {
		assertThrows(IllegalArgumentException.class, () -> new AdoptionContext(REPOSITORY_URL, null));
	}

	/**
	 * An omitted command-line positional and an empty MCP argument both mean "adopt
	 * on the default branch", so neither may be rejected as an invalid branch.
	 */
	@Test
	void aBlankOrAbsentBranchFallsBackToTheDefault() {
		assertEquals(AdoptionContext.DEFAULT_BRANCH, AdoptionContext.branchOrDefault(null));
		assertEquals(AdoptionContext.DEFAULT_BRANCH, AdoptionContext.branchOrDefault("   "));
	}

	@Test
	void aNamedBranchOverridesTheDefault() {
		assertEquals("feature/adopt", AdoptionContext.branchOrDefault("  feature/adopt  "));
	}

	/** The clone needs the credentials; everything that reports the run must not carry them. */
	@Test
	void reportsTheRepositoryWithoutTheCredentialsItClonesWith() {
		AdoptionContext context = new AdoptionContext("https://x-access-token:secret@github.com/owner/repo.git",
				Path.of("/tmp/workspace"));
		assertEquals("https://x-access-token:secret@github.com/owner/repo.git", context.repositoryUrl());
		assertEquals("https://***@github.com/owner/repo.git", context.displayUrl());
	}

	/** What the checkout is left recording: a usable URL that carries no secret. */
	@Test
	void answersACredentialFreeUrlForTheCheckoutToKeep() {
		AdoptionContext context = new AdoptionContext("https://x-access-token:secret@github.com/owner/repo.git",
				Path.of("/tmp/workspace"));
		assertEquals("https://github.com/owner/repo.git", context.checkoutUrl());
	}

	/**
	 * A step deciding whether a checkout it found is the repository under adoption
	 * asks the context, so the credentialled clone URL stays where only the clone
	 * command sees it.
	 */
	@Test
	void answersWhetherAnotherUrlNamesTheRepositoryUnderAdoption() {
		AdoptionContext context = new AdoptionContext("https://x-access-token:secret@github.com/owner/repo.git",
				Path.of("/tmp/workspace"));
		assertTrue(context.isSameRepository("git@github.com:owner/repo.git"));
		assertFalse(context.isSameRepository("https://github.com/someone-else/repo.git"));
	}
}

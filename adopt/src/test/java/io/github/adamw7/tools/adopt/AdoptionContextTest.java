package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdoptionContextTest {

	private final Path workspace = Path.of("/tmp/workspace");

	/**
	 * An empty last segment would resolve the checkout onto the workspace itself,
	 * so the clone would land beside the other repositories the workspace holds
	 * instead of in its own directory.
	 */
	@Test
	void aUrlWithNoRepositoryNameIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new AdoptionContext("https://github.com/adamw7//", Path.of("/tmp/workspace")));
		assertThrows(IllegalArgumentException.class,
				() -> new AdoptionContext("https://github.com/adamw7/.git", Path.of("/tmp/workspace")));
	}

	@Test
	void aUrlWhoseNameIsADirectoryAliasIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new AdoptionContext("..", Path.of("/tmp/workspace")));
		assertThrows(IllegalArgumentException.class, () -> new AdoptionContext(".", Path.of("/tmp/workspace")));
	}

	@Test
	void derivesCheckoutDirectoryFromHttpsUrl() {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git", workspace);
		assertEquals(workspace.resolve("tools"), context.repositoryDirectory());
	}

	@Test
	void derivesCheckoutDirectoryWithoutGitSuffix() {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools", workspace);
		assertEquals(workspace.resolve("tools"), context.repositoryDirectory());
	}

	@Test
	void handlesTrailingSlash() {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools/", workspace);
		assertEquals(workspace.resolve("tools"), context.repositoryDirectory());
	}

	@Test
	void derivesCheckoutDirectoryFromSshUrl() {
		AdoptionContext context = new AdoptionContext("git@github.com:adamw7/tools.git", workspace);
		assertEquals(workspace.resolve("tools"), context.repositoryDirectory());
	}

	@Test
	void derivesCheckoutDirectoryFromNestedGroupUrl() {
		AdoptionContext context = new AdoptionContext("https://gitlab.com/group/subgroup/tools.git", workspace);
		assertEquals(workspace.resolve("tools"), context.repositoryDirectory());
	}

	@Test
	void keepsDotsInRepositoryNameThatAreNotTheGitSuffix() {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/my.tool.git", workspace);
		assertEquals(workspace.resolve("my.tool"), context.repositoryDirectory());
	}

	@Test
	void exposesTheWorkspaceDirectory() {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git", workspace);
		assertEquals(workspace, context.workspace());
	}

	@Test
	void trimsAndKeepsUrl() {
		AdoptionContext context = new AdoptionContext("  https://github.com/adamw7/tools.git  ", workspace);
		assertEquals("https://github.com/adamw7/tools.git", context.repositoryUrl());
	}

	@Test
	void defaultsToTheAdoptionFeatureBranch() {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git", workspace);
		assertEquals(AdoptionContext.DEFAULT_BRANCH, context.branchName());
	}

	@Test
	void keepsAndTrimsSuppliedBranchName() {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git", workspace,
				"  feature/x  ");
		assertEquals("feature/x", context.branchName());
	}

	@Test
	void rejectsBlankBranchName() {
		assertThrows(IllegalArgumentException.class,
				() -> new AdoptionContext("https://github.com/adamw7/tools.git", workspace, "  "));
	}

	@Test
	void rejectsBlankUrl() {
		assertThrows(IllegalArgumentException.class, () -> new AdoptionContext("  ", workspace));
	}

	@Test
	void rejectsNullUrl() {
		assertThrows(IllegalArgumentException.class, () -> new AdoptionContext(null, workspace));
	}

	@Test
	void rejectsNullBranchName() {
		assertThrows(IllegalArgumentException.class,
				() -> new AdoptionContext("https://github.com/adamw7/tools.git", workspace, null));
	}

	@Test
	void rejectsNullWorkspace() {
		assertThrows(IllegalArgumentException.class,
				() -> new AdoptionContext("https://github.com/adamw7/tools.git", null));
	}

	@Test
	void derivesTheOwnerAndRepositoryFromAnHttpsUrl() {
		assertEquals("adamw7/tools",
				new AdoptionContext("https://github.com/adamw7/tools.git", workspace).repositorySlug().orElseThrow());
		assertEquals("adamw7/tools",
				new AdoptionContext("https://github.com/adamw7/tools", workspace).repositorySlug().orElseThrow());
		assertEquals("adamw7/tools",
				new AdoptionContext("https://github.com/adamw7/tools/", workspace).repositorySlug().orElseThrow());
	}

	/**
	 * The scp-like form separates the host from the path with ':', not '/', so a
	 * naive split on '/' alone would read the host and owner as one segment.
	 */
	@Test
	void derivesTheOwnerAndRepositoryFromSshForms() {
		assertEquals("adamw7/tools",
				new AdoptionContext("git@github.com:adamw7/tools.git", workspace).repositorySlug().orElseThrow());
		assertEquals("adamw7/tools", new AdoptionContext("ssh://git@github.com/adamw7/tools.git", workspace)
				.repositorySlug().orElseThrow());
	}

	@Test
	void derivesTheOwnerAndRepositoryFromAnEnterpriseHost() {
		assertEquals("adamw7/tools", new AdoptionContext("https://github.example.com/adamw7/tools.git", workspace)
				.repositorySlug().orElseThrow());
	}

	/**
	 * A filesystem path names no owner, so reading its last two segments as one
	 * would point a tool at a repository that does not exist. Reporting no slug
	 * leaves the caller to fall back to its own inference.
	 */
	@Test
	void aUrlWithNoHostNamesNoOwner() {
		assertTrue(new AdoptionContext("/tmp/workspace/tools", workspace).repositorySlug().isEmpty());
		assertTrue(new AdoptionContext("file:///tmp/tools", workspace).repositorySlug().isEmpty());
		assertTrue(new AdoptionContext("tools", workspace).repositorySlug().isEmpty());
	}
}

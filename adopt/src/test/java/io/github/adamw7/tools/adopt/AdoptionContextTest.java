package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdoptionContextTest {

	private final Path workspace = Path.of("/tmp/workspace");

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
}

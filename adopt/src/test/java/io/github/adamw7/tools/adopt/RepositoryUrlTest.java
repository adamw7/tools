package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RepositoryUrlTest {

	/**
	 * An empty last segment would resolve the checkout onto the workspace itself,
	 * so the clone would land beside the other repositories the workspace holds
	 * instead of in its own directory.
	 */
	@Test
	void aUrlWithNoRepositoryNameIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of("https://github.com/adamw7//"));
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of("https://github.com/adamw7/.git"));
	}

	@Test
	void aUrlWhoseNameIsADirectoryAliasIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of(".."));
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of("."));
	}

	@Test
	void rejectsBlankUrl() {
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of("  "));
	}

	@Test
	void rejectsNullUrl() {
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of(null));
	}

	@Test
	void trimsAndKeepsUrl() {
		assertEquals("https://github.com/adamw7/tools.git",
				RepositoryUrl.of("  https://github.com/adamw7/tools.git  ").value());
	}

	@Test
	void derivesTheNameFromAnHttpsUrl() {
		assertEquals("tools", RepositoryUrl.of("https://github.com/adamw7/tools.git").name());
		assertEquals("tools", RepositoryUrl.of("https://github.com/adamw7/tools").name());
		assertEquals("tools", RepositoryUrl.of("https://github.com/adamw7/tools/").name());
	}

	@Test
	void derivesTheNameFromAnSshUrl() {
		assertEquals("tools", RepositoryUrl.of("git@github.com:adamw7/tools.git").name());
	}

	@Test
	void derivesTheNameFromANestedGroupUrl() {
		assertEquals("tools", RepositoryUrl.of("https://gitlab.com/group/subgroup/tools.git").name());
	}

	@Test
	void keepsDotsInTheNameThatAreNotTheGitSuffix() {
		assertEquals("my.tool", RepositoryUrl.of("https://github.com/adamw7/my.tool.git").name());
	}

	@Test
	void derivesTheOwnerAndRepositoryFromAnHttpsUrl() {
		assertEquals("adamw7/tools", RepositoryUrl.of("https://github.com/adamw7/tools.git").slug().orElseThrow());
		assertEquals("adamw7/tools", RepositoryUrl.of("https://github.com/adamw7/tools").slug().orElseThrow());
		assertEquals("adamw7/tools", RepositoryUrl.of("https://github.com/adamw7/tools/").slug().orElseThrow());
	}

	/**
	 * The scp-like form separates the host from the path with ':', not '/', so a
	 * naive split on '/' alone would read the host and owner as one segment.
	 */
	@Test
	void derivesTheOwnerAndRepositoryFromSshForms() {
		assertEquals("adamw7/tools", RepositoryUrl.of("git@github.com:adamw7/tools.git").slug().orElseThrow());
		assertEquals("adamw7/tools", RepositoryUrl.of("ssh://git@github.com/adamw7/tools.git").slug().orElseThrow());
	}

	@Test
	void derivesTheOwnerAndRepositoryFromAnEnterpriseHost() {
		assertEquals("adamw7/tools",
				RepositoryUrl.of("https://github.example.com/adamw7/tools.git").slug().orElseThrow());
	}

	/**
	 * A filesystem path names no owner, so reading its last two segments as one
	 * would point a tool at a repository that does not exist. Reporting no slug
	 * leaves the caller to fall back to its own inference.
	 */
	@Test
	void aUrlWithNoHostNamesNoOwner() {
		assertTrue(RepositoryUrl.of("/tmp/workspace/tools").slug().isEmpty());
		assertTrue(RepositoryUrl.of("file:///tmp/tools").slug().isEmpty());
		assertTrue(RepositoryUrl.of("tools").slug().isEmpty());
	}

	/**
	 * git is handed the URL as given, but everything that outlives the run — the log,
	 * a failure message, the JSON report — reports the masked form.
	 */
	@Test
	void keepsTheCredentialsGitNeedsAndMasksTheOnesItReports() {
		RepositoryUrl url = RepositoryUrl.of("https://x-access-token:secret@github.com/owner/repo.git");
		assertEquals("https://x-access-token:secret@github.com/owner/repo.git", url.value());
		assertEquals("https://***@github.com/owner/repo.git", url.redacted());
	}

	@Test
	void aUrlWithoutCredentialsReadsTheSameEitherWay() {
		RepositoryUrl url = RepositoryUrl.of("https://github.com/owner/repo.git");
		assertEquals(url.value(), url.redacted());
	}

	/** A URL refused on its input is quoted back, so it must be masked there too. */
	@Test
	void masksTheCredentialsOfARejectedUrl() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> RepositoryUrl.of("https://x-access-token:secret@github.com/owner/.git"));
		assertTrue(exception.getMessage().contains("https://***@github.com/owner/.git"), exception.getMessage());
	}
}

package io.github.adamw7.tools.adopt;

import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	/**
	 * The scp-like form's ':' is a path separator, so a host-only URL ends at it. A
	 * name taken from the last '/' alone read the whole URL as the repository and
	 * named the checkout directory {@code git@host:tools}, which a filesystem that
	 * reserves ':' cannot even resolve.
	 */
	@Test
	void derivesTheNameFromAnSshUrlThatNamesNoOwner() {
		assertEquals("tools", RepositoryUrl.of("git@host:tools.git").name());
		assertEquals("tools", RepositoryUrl.of("git@host:tools").name());
	}

	/**
	 * git clones {@code .../tools.GIT} as readily as {@code .../tools.git}, and both
	 * name the one repository — so keeping the case would name the checkout
	 * {@code tools.GIT} and ask GitHub about a repository that answers 404.
	 */
	@Test
	void stripsTheGitSuffixWhateverItsCase() {
		assertEquals("tools", RepositoryUrl.of("https://github.com/adamw7/tools.GIT").name());
		assertEquals("adamw7/tools", RepositoryUrl.of("https://github.com/adamw7/tools.GIT").slug().orElseThrow());
	}

	/**
	 * A last segment carrying a backslash is a path rather than a name, and resolving
	 * one against the workspace would put the checkout outside it wherever '\' is a
	 * separator.
	 */
	@Test
	void aUrlWhoseNameIsAPathIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> RepositoryUrl.of("https://github.com/adamw7/a\\..\\..\\evil"));
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of("C:\\repos\\tools"));
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
	 * An enterprise host reached on its own port is the ordinary shape of a
	 * self-hosted GitHub. Splitting on ':' — which the scp-like form needs — read the
	 * port as a path segment of its own, so the host check landed on {@code 8443} and
	 * the URL was reported as naming no owner at all.
	 */
	@Test
	void derivesTheOwnerAndRepositoryFromAHostReachedOnAPort() {
		assertEquals("adamw7/tools",
				RepositoryUrl.of("https://ghe.example.com:8443/adamw7/tools.git").slug().orElseThrow());
		assertEquals("adamw7/tools",
				RepositoryUrl.of("https://ghe.example.com:8443/adamw7/tools").slug().orElseThrow());
		assertEquals("adamw7/tools",
				RepositoryUrl.of("https://ghe.example.com:8443/adamw7/tools/").slug().orElseThrow());
	}

	@Test
	void derivesTheOwnerAndRepositoryFromAnSshUrlOnANonDefaultPort() {
		assertEquals("adamw7/tools",
				RepositoryUrl.of("ssh://git@ghe.example.com:2222/adamw7/tools.git").slug().orElseThrow());
	}

	/** The port sits behind the credentials, so both have to be stepped over at once. */
	@Test
	void derivesTheOwnerAndRepositoryFromACredentialledUrlOnAPort() {
		assertEquals("adamw7/tools",
				RepositoryUrl.of("https://x-access-token:secret@ghe.example.com:8443/adamw7/tools.git")
						.slug().orElseThrow());
	}

	/** A port is no part of the checkout directory's name either. */
	@Test
	void derivesTheNameFromAHostReachedOnAPort() {
		assertEquals("tools", RepositoryUrl.of("https://ghe.example.com:8443/adamw7/tools.git").name());
		assertEquals("tools", RepositoryUrl.of("ssh://git@ghe.example.com:2222/adamw7/tools").name());
	}

	/** Dropping the port must not promote the host into the owner's slot. */
	@Test
	void aUrlOnAPortThatNamesNoOwnerStillNamesNone() {
		assertTrue(RepositoryUrl.of("https://ghe.example.com:8443/tools.git").slug().isEmpty());
	}

	/**
	 * The scp-like form carries no port — that is what {@code ssh://} is for — so its
	 * ':' always separates a path, even before an owner that happens to read like one.
	 * Stripping it here would have turned {@code 2222/tools} into a repository with no
	 * owner.
	 */
	@Test
	void anScpLikeUrlsColonIsAPathSeparatorEvenBeforeDigits() {
		assertEquals("2222/tools", RepositoryUrl.of("git@github.com:2222/tools.git").slug().orElseThrow());
		assertEquals("tools", RepositoryUrl.of("git@github.com:2222/tools.git").name());
	}

	/**
	 * Two ports on one host are two servers. The comparison may only err towards
	 * refusing a checkout, since accepting the wrong one commits to it, pushes it,
	 * and opens its pull request.
	 */
	@Test
	void twoPortsOnOneHostAreDifferentRepositories() {
		RepositoryUrl url = RepositoryUrl.of("https://ghe.example.com:8443/adamw7/tools.git");
		assertFalse(url.isSameRepositoryAs("https://ghe.example.com:9443/adamw7/tools.git"));
		assertFalse(url.isSameRepositoryAs("https://ghe.example.com/adamw7/tools.git"));
	}

	/** The forms of one repository on a ported host still name that one repository. */
	@Test
	void everyFormOfOneRepositoryOnAPortedHostIsTheSameRepository() {
		RepositoryUrl url = RepositoryUrl.of("https://ghe.example.com:8443/adamw7/tools.git");
		assertTrue(url.isSameRepositoryAs("https://ghe.example.com:8443/adamw7/tools"));
		assertTrue(url.isSameRepositoryAs("https://ghe.example.com:8443/adamw7/Tools.GIT"));
		assertTrue(url.isSameRepositoryAs("https://token@ghe.example.com:8443/adamw7/tools.git"));
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
		assertEquals(url.value(), url.withoutCredentials());
	}

	/**
	 * What the checkout records as its {@code origin}: the credentials are removed
	 * rather than masked, because the masked form names a host called {@code ***@…}
	 * that git cannot fetch from.
	 */
	@Test
	void dropsTheCredentialsForTheUrlTheCheckoutKeeps() {
		RepositoryUrl url = RepositoryUrl.of("https://x-access-token:secret@github.com/owner/repo.git");
		assertEquals("https://github.com/owner/repo.git", url.withoutCredentials());
		assertEquals("https://github.com/owner/repo.git",
				RepositoryUrl.of("https://token@github.com/owner/repo.git").withoutCredentials());
	}

	/**
	 * A password may carry an unencoded {@code @}, which makes the credentials run to
	 * the <em>last</em> one before the path — the same reading {@link Redaction} takes.
	 * Stopping at the first would leave the tail of the password in the URL written to
	 * {@code .git/config}, which is the one place this exists to keep it out of.
	 */
	@Test
	void dropsAPasswordCarryingAnAtSign() {
		assertEquals("https://github.com/owner/repo.git",
				RepositoryUrl.of("https://user:p@ss@github.com/owner/repo.git").withoutCredentials());
	}

	/**
	 * An SSH URL's user is the account to log in as, not a secret, so it is kept: a
	 * {@code git@} dropped from one would authenticate as whoever is running the
	 * adoption and be refused by the host.
	 */
	@Test
	void keepsTheUserOfAnSshUrl() {
		assertEquals("git@github.com:owner/repo.git",
				RepositoryUrl.of("git@github.com:owner/repo.git").withoutCredentials());
		assertEquals("ssh://git@github.com/owner/repo.git",
				RepositoryUrl.of("ssh://git@github.com/owner/repo.git").withoutCredentials());
	}

	/** A URL refused on its input is quoted back, so it must be masked there too. */
	@Test
	void masksTheCredentialsOfARejectedUrl() {
		assertFailure(IllegalArgumentException.class,
				() -> RepositoryUrl.of("https://x-access-token:secret@github.com/owner/.git"),
				"https://***@github.com/owner/.git");
	}

	/**
	 * The forms one repository is cloned by: GitHub offers its HTTPS and SSH URLs
	 * side by side, CI adds a token, and a checkout's recorded {@code origin} may be
	 * any of them. All name the repository the adoption was asked for.
	 */
	@Test
	void everyFormOfOneRepositoryIsTheSameRepository() {
		RepositoryUrl url = RepositoryUrl.of("https://github.com/octocat/Hello-World.git");
		assertTrue(url.isSameRepositoryAs("https://github.com/octocat/Hello-World.git"));
		assertTrue(url.isSameRepositoryAs("https://github.com/octocat/Hello-World"));
		assertTrue(url.isSameRepositoryAs("https://github.com/octocat/hello-world.GIT"));
		assertTrue(url.isSameRepositoryAs("https://github.com/octocat/Hello-World/"));
		assertTrue(url.isSameRepositoryAs("git@github.com:octocat/Hello-World.git"));
		assertTrue(url.isSameRepositoryAs("ssh://git@github.com/octocat/Hello-World"));
		assertTrue(url.isSameRepositoryAs("https://x-access-token:secret@github.com/octocat/Hello-World.git"));
	}

	/**
	 * A checkout directory is named after the repository alone, so these are the
	 * URLs that claim one directory while naming different repositories — the
	 * collision the reuse of an existing checkout has to catch.
	 */
	@Test
	void twoOwnersOfOneRepositoryNameAreDifferentRepositories() {
		RepositoryUrl url = RepositoryUrl.of("https://github.com/alice/tools.git");
		assertFalse(url.isSameRepositoryAs("https://github.com/bob/tools.git"));
		assertFalse(url.isSameRepositoryAs("https://gitlab.com/alice/tools.git"));
		assertFalse(url.isSameRepositoryAs("/tmp/workspace/tools"));
	}

	/** Text that names no repository matches none, rather than every URL or the last one asked. */
	@Test
	void noUrlAtAllIsNoRepository() {
		RepositoryUrl url = RepositoryUrl.of("https://github.com/alice/tools.git");
		assertFalse(url.isSameRepositoryAs(null));
		assertFalse(url.isSameRepositoryAs(""));
		assertFalse(url.isSameRepositoryAs("  "));
	}

	/**
	 * A URL that is credentials and no path names no repository, and reduced to the
	 * same empty identity as text that names none — so it answered every blank line
	 * of a checkout's {@code origin} transcript as a match on itself, and the
	 * adoption branched, committed, and pushed into whatever checkout it found.
	 */
	@Test
	void aUrlThatIsOnlyUserInformationIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of("https://token@"));
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of("https://x-access-token:secret@"));
		assertThrows(IllegalArgumentException.class, () -> RepositoryUrl.of("git@"));
	}

	/** An {@code @} inside a checkout name is not user information; only a trailing one is. */
	@Test
	void aRepositoryNameCarryingAnAtSignIsAccepted() {
		assertEquals("foo@bar", RepositoryUrl.of("/tmp/workspace/foo@bar").name());
		assertEquals("tools", RepositoryUrl.of("https://x-access-token:secret@github.com/alice/tools.git").name());
	}
}

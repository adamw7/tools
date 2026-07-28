package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RedactionTest {

	/** The shape a CI runner hands the adoption: the token is the password. */
	@Test
	void masksAUserAndPasswordInACloneUrl() {
		assertEquals("https://***@github.com/owner/repo.git",
				Redaction.of("https://x-access-token:secret@github.com/owner/repo.git"));
	}

	/** An access token is often the whole user information, with no password at all. */
	@Test
	void masksATokenUsedAsTheUserName() {
		assertEquals("https://***@github.com/owner/repo.git",
				Redaction.of("https://a-token-value@github.com/owner/repo.git"));
	}

	@Test
	void leavesAUrlWithoutCredentialsAlone() {
		assertEquals("https://github.com/owner/repo.git", Redaction.of("https://github.com/owner/repo.git"));
	}

	/**
	 * The scp-like form's {@code git@} is the well-known user every such URL carries,
	 * not a credential, so masking it would only make the URL harder to recognise.
	 */
	@Test
	void leavesTheScpFormsWellKnownUserAlone() {
		assertEquals("git@github.com:owner/repo.git", Redaction.of("git@github.com:owner/repo.git"));
	}

	/** A transcript carries the URL inside a sentence, not on its own. */
	@Test
	void masksCredentialsAnywhereInTheText() {
		String masked = Redaction.of("fatal: could not read Username for 'https://user:pw@github.com': no such device");
		assertEquals("fatal: could not read Username for 'https://***@github.com': no such device", masked);
		assertFalse(masked.contains("pw"), masked);
	}

	/**
	 * git ends the user information at the last '@' before the host, so a password
	 * carrying an unencoded one is a single credential. Masking only as far as the
	 * first '@' left the rest of it in the log, the failure message, and the report.
	 */
	@Test
	void masksAPasswordThatCarriesAnAtSign() {
		String masked = Redaction.of("https://user:p@ss@github.com/owner/repo.git");
		assertEquals("https://***@github.com/owner/repo.git", masked);
		assertFalse(masked.contains("ss@github"), masked);
	}

	@Test
	void masksEveryUrlOfATextThatCarriesSeveral() {
		assertEquals("https://***@github.com/a.git and https://***@github.com/b.git",
				Redaction.of("https://u:1@github.com/a.git and https://v:2@github.com/b.git"));
	}

	@Test
	void leavesTextWithNoUrlAlone() {
		assertEquals("clone: repository not found", Redaction.of("clone: repository not found"));
	}

	/** A command's captured output can be absent, so callers must not have to null-check first. */
	@Test
	void answersNullForNull() {
		assertNull(Redaction.of(null));
	}

	@Test
	void answersBlankForBlank() {
		assertEquals("", Redaction.of(""));
	}
}

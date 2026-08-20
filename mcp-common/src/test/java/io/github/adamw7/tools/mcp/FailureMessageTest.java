package io.github.adamw7.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class FailureMessageTest {

	private static final String TOKEN = "ghp-a-real-looking-token";

	/**
	 * The argument an {@code adopt_repo} call carries is the clone URL, credentials and
	 * all. The log line is the likeliest place for a token to reach disk, and this
	 * server outlives the call by days.
	 */
	@Test
	public void logKeepsTheArgumentsWithoutTheirCredentials() {
		String logged = FailureMessage.forLog(
				Map.of("repository_url", "https://x-access-token:" + TOKEN + "@github.com/owner/repo.git"));

		assertFalse(logged.contains(TOKEN), logged);
		assertTrue(logged.contains("repository_url"), logged);
		assertTrue(logged.contains("github.com/owner/repo.git"), logged);
	}

	@Test
	public void logDescribesACallWithNoArguments() {
		assertEquals("{}", FailureMessage.forLog(Map.of()));
	}

	/**
	 * A failing command quotes the URL it was given back in its own message, so the
	 * answer has to be masked as well as the log.
	 */
	@Test
	public void clientMessageCarriesNoCredentials() {
		String message = FailureMessage.forClient(new IllegalStateException(
				"clone failed for https://" + TOKEN + "@github.com/owner/repo.git"));

		assertEquals("clone failed for https://***@github.com/owner/repo.git", message);
	}

	/**
	 * The path a denied call resolved to is the server's, not the caller's: answering
	 * with it hands back the very layout the path confinement exists to withhold. The
	 * leaf is kept, because that is what the caller named and what says what went wrong.
	 */
	@Test
	public void clientMessageKeepsTheFileButNotWhereItLives() {
		String message = FailureMessage.forClient(new SecurityException(
				"Access denied: path '/srv/secrets/project/report.csv' is outside the allowed base directory "
						+ "'/srv/mcp/allowed'"));

		assertEquals("Access denied: path '.../report.csv' is outside the allowed base directory '.../allowed'",
				message);
	}

	@Test
	public void clientMessageAbbreviatesAWindowsPath() {
		String message = FailureMessage.forClient(
				new IllegalArgumentException("Invalid file path: C:\\Users\\operator\\data\\report.csv"));

		assertEquals("Invalid file path: ...\\report.csv", message);
	}

	/**
	 * A URL's path is part of the URL, not a directory on this machine, so masking it
	 * would only make the failure harder to read.
	 */
	@Test
	public void clientMessageLeavesAUrlPathAlone() {
		String message = FailureMessage.forClient(
				new IllegalStateException("gh: no commits between main and https://github.com/owner/repo/pull/7"));

		assertEquals("gh: no commits between main and https://github.com/owner/repo/pull/7", message);
	}

	@Test
	public void clientMessageLeavesAPlainFailureAlone() {
		assertEquals("Missing required argument: file",
				FailureMessage.forClient(new IllegalArgumentException("Missing required argument: file")));
	}

	/**
	 * A relative path names nothing about the server's layout, and is what the caller
	 * asked with, so it is answered as it was given.
	 */
	@Test
	public void clientMessageLeavesARelativePathAlone() {
		assertEquals("Class not found in src/main/java: Missing",
				FailureMessage.forClient(new IllegalArgumentException("Class not found in src/main/java: Missing")));
	}

	/**
	 * An exception thrown with no message of its own still has to name something, or
	 * the client is told only that the call failed.
	 */
	@Test
	public void clientMessageNamesTheExceptionWhenItCarriesNoMessage() {
		assertEquals("java.lang.NullPointerException", FailureMessage.forClient(new NullPointerException()));
	}
}

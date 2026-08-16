package io.github.adamw7.tools.adopt.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TransientFailuresTest {

	private static final List<String> CLONE = List.of("git", "clone", "https://github.com/octocat/Hello-World.git",
			"/tmp/workspace/Hello-World");

	private static final List<String> PULL_REQUEST = List.of("gh", "pr", "create");

	@Test
	void retriesAGitCommandTheNetworkRefused() {
		assertRetried(CLONE,
				"fatal: unable to access 'https://github.com/octocat/Hello-World.git/':"
						+ " Could not resolve host: github.com",
				"fatal: unable to access 'https://github.com/o/r.git/': Failed to connect to github.com port 443:"
						+ " Connection timed out",
				"error: RPC failed; curl 56 Recv failure: Connection reset by peer",
				"fatal: the remote end hung up unexpectedly",
				"error: RPC failed; curl 92 HTTP/2 stream was not closed cleanly" + System.lineSeparator()
						+ "fatal: early EOF",
				"fatal: unable to access 'https://github.com/o/r.git/': The requested URL returned error: 503");
	}

	@Test
	void retriesAGhCommandTheNetworkRefused() {
		assertRetried(PULL_REQUEST,
				"error connecting to api.github.com: dial tcp: lookup api.github.com: no such host",
				"Post \"https://api.github.com/graphql\": net/http: TLS handshake timeout",
				"HTTP 502: Bad gateway (https://api.github.com/graphql)");
	}

	@Test
	void matchesTheToolsWordingWhateverItsCase() {
		assertRetried(CLONE, "FATAL: COULD NOT RESOLVE HOST: GITHUB.COM");
	}

	@Test
	void doesNotRetryACommandThatSucceeded() {
		assertFalse(TransientFailures.worthRetrying(new CommandResult(CLONE, 0, "Cloning into 'Hello-World'...")));
	}

	/**
	 * The failures an adoption is meant to report rather than to sit through. A 403 in
	 * particular shares its sentence with the 5xx that <em>is</em> retried, so it is
	 * the case that says the status code is read rather than the wording around it.
	 */
	@Test
	void doesNotRetryAFailureAnotherAttemptCannotSurvive() {
		assertNotRetried(CLONE,
				"fatal: unable to access 'https://github.com/o/r.git/': The requested URL returned error: 403",
				"remote: Repository not found." + System.lineSeparator()
						+ "fatal: repository 'https://github.com/o/r.git/' not found",
				"fatal: could not read Username for 'https://github.com': terminal prompts disabled",
				"! [rejected] claude/adopt-claude-code -> claude/adopt-claude-code (non-fast-forward)");
	}

	/**
	 * The pipeline asks git questions whose answer is a non-zero exit with nothing to
	 * say — is there a local branch, is anything staged — and every one of them must
	 * be answered on the first attempt.
	 */
	@Test
	void doesNotRetryAQueryAnsweringThroughItsExitCode() {
		assertNotRetried(List.of("git", "rev-parse", "--verify", "--quiet", "refs/heads/claude/adopt-claude-code"), "");
	}

	/**
	 * A model's prose and a project's test output are not the tools' own wording, so a
	 * transcript that merely discusses a connection reset is not one — and re-running
	 * either command costs the run minutes.
	 */
	@Test
	void doesNotRetryACommandThatIsNotGitOrGh() {
		String prose = "The retry logic handles connection reset by peer";
		assertNotRetried(List.of("claude", "-p", "/init"), prose);
		assertNotRetried(List.of("mvn", "-B", "verify"), prose);
		assertNotRetried(List.of("gradlew", "check"), prose);
	}

	@Test
	void doesNotRetryACommandWithNoProgramToName() {
		assertNotRetried(List.of(), "could not resolve host");
	}

	/**
	 * A secondary rate limit asks for a wait measured in minutes, so retrying it
	 * seconds later is what the limit exists to stop.
	 */
	@Test
	void doesNotRetryARateLimit() {
		assertNotRetried(PULL_REQUEST,
				"You have exceeded a secondary rate limit. Please wait a few minutes before you try again.");
	}

	private void assertRetried(List<String> command, String... transcripts) {
		for (String transcript : transcripts) {
			assertTrue(TransientFailures.worthRetrying(new CommandResult(command, 128, transcript)), transcript);
		}
	}

	private void assertNotRetried(List<String> command, String... transcripts) {
		for (String transcript : transcripts) {
			assertFalse(TransientFailures.worthRetrying(new CommandResult(command, 128, transcript)), transcript);
		}
	}
}

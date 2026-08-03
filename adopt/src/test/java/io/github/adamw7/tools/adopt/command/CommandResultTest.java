package io.github.adamw7.tools.adopt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommandResultTest {

	@Test
	void zeroExitCodeSucceeds() {
		CommandResult result = new CommandResult(List.of("git", "status"), 0, "clean");
		assertTrue(result.succeeded());
	}

	@Test
	void nonZeroExitCodeFails() {
		CommandResult result = new CommandResult(List.of("git", "push"), 1, "rejected");
		assertFalse(result.succeeded());
	}

	@Test
	void describeJoinsCommandTokens() {
		CommandResult result = new CommandResult(List.of("git", "commit", "-m", "msg"), 0, "");
		assertEquals("git commit -m msg", result.describe());
	}

	/** The failures raised before a result exists describe their command the same way. */
	@Test
	void describesACommandWithoutAResult() {
		assertEquals("gh pr create", CommandResult.describe(List.of("gh", "pr", "create")));
	}

	/**
	 * Every failure message quotes the command that raised it, and those messages are
	 * logged and written to the run's report, so a clone URL's credentials must not
	 * survive the one place a command is turned into text.
	 */
	@Test
	void masksTheCredentialsOfAClonedUrl() {
		String described = CommandResult.describe(
				List.of("git", "clone", "https://x-access-token:secret@github.com/owner/repo.git", "/tmp/repo"));
		assertEquals("git clone https://***@github.com/owner/repo.git /tmp/repo", described);
	}

	/**
	 * A tool handed a credentialled clone URL echoes it back when it cannot use it, so
	 * the transcript carries a secret as readily as the command does — and the
	 * transcript is what the failure messages and the warnings quote.
	 */
	@Test
	void masksTheCredentialsAToolEchoedBackInItsTranscript() {
		CommandResult result = new CommandResult(List.of("git", "fetch"), 128,
				"fatal: could not read Username for 'https://x-access-token:secret@github.com'");
		assertEquals("fatal: could not read Username for 'https://***@github.com'", result.redactedOutput());
	}

	/** A transcript carrying no credentials reads as the tool wrote it. */
	@Test
	void leavesATranscriptWithoutCredentialsAlone() {
		assertEquals("nothing to commit", new CommandResult(List.of("git", "commit"), 1, "nothing to commit")
				.redactedOutput());
	}
}

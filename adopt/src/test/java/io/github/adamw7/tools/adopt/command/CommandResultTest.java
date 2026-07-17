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
}

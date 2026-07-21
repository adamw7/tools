package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FallbackBuildSystemTest {

	private final FallbackBuildSystem buildSystem = new FallbackBuildSystem();

	@Test
	void matchesEveryCheckoutSoNothingIsLeftWithoutAGuard(@TempDir Path directory) {
		assertTrue(buildSystem.matches(directory));
	}

	@Test
	void isNamedForItsGitHubActionsGuard() {
		assertEquals("github-actions", buildSystem.name());
	}

	@Test
	void verifyCommandRunsTheGuardScript() {
		assertEquals(List.of("sh", ".github/claude-md-guard.sh"), buildSystem.verifyCommand());
	}

	@Test
	void installWritesTheWorkflowGuard(@TempDir Path directory) {
		assertTrue(buildSystem.install(directory));
		assertTrue(Files.isRegularFile(directory.resolve(WorkflowGuardInstaller.WORKFLOW_FILE)));
	}

	@Test
	void installIsIdempotent(@TempDir Path directory) {
		buildSystem.install(directory);
		assertFalse(buildSystem.install(directory));
	}
}

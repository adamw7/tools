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
	void verifyCommandRunsTheGuardScript(@TempDir Path directory) {
		assertEquals(List.of("sh", ".github/claude-md-guard.sh"), buildSystem.verifyCommand(directory));
	}

	/**
	 * The guard's shell is a tool like any other, so it is probed before the adoption
	 * spends a clone and a claude init on a checkout it could not have verified.
	 */
	@Test
	void probesTheShellRatherThanAssumingEveryHostHasOne(@TempDir Path directory) {
		assertEquals(List.of("sh", "-c", "exit 0"), buildSystem.toolProbe(directory).orElseThrow());
	}

	/** {@code sh --version} exits non-zero under dash, so the probe must not ask for one. */
	@Test
	void probesWithSomethingEveryPosixShellAnswers(@TempDir Path directory) {
		assertFalse(buildSystem.toolProbe(directory).orElseThrow().contains("--version"));
	}

	@Test
	void probesAndVerifiesWithTheOneShell(@TempDir Path directory) {
		assertEquals(buildSystem.verifyCommand(directory).get(0),
				buildSystem.toolProbe(directory).orElseThrow().get(0));
	}

	/** Nothing here is installable under the build system's own name. */
	@Test
	void advisesAboutAShellRatherThanAboutItself() {
		String advice = buildSystem.toolAdvice();
		assertTrue(advice.contains("sh"), advice);
		assertFalse(advice.contains(buildSystem.name()), advice);
	}

	/** The script the verification runs is the one the workflow installs, not a second copy. */
	@Test
	void verifiesTheScriptTheGuardInstalls(@TempDir Path directory) {
		buildSystem.install(directory);
		assertTrue(Files.isRegularFile(directory.resolve(buildSystem.verifyCommand(directory).get(1))));
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

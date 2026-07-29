package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionContexts;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class BuildToolchainStepTest {

	@Test
	void probesMavenForAMavenCheckout(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "pom.xml", "<project/>");
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new BuildToolchainStep().execute(context, runner);
		assertEquals(List.of("mvn", "--version"), runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
	}

	@Test
	void probesGradleForAGradleCheckout(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "build.gradle", "plugins { id 'java' }\n");
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new BuildToolchainStep().execute(context, runner);
		assertEquals(List.of("gradle", "--version"), runner.commandAt(0));
	}

	/**
	 * Without this the adoption only finds out at {@link VerifyStep}, once a clone,
	 * a claude init, a reshaped CLAUDE.md, and two commits have already been spent
	 * on a checkout it was never going to be able to verify.
	 */
	@Test
	void anAbsentBuildToolAbortsTheAdoption(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "build.gradle", "plugins { id 'java' }\n");
		RecordingCommandRunner runner = RecordingCommandRunner.failing(127, "gradle: not found");
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> new BuildToolchainStep().execute(context, runner));
		assertTrue(thrown.getMessage().contains("gradle"), thrown.getMessage());
	}

	@Test
	void aBuildToolThatCannotStartAbortsTheAdoption(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "pom.xml", "<project/>");
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			throw new AdoptionException("Could not start command: " + String.join(" ", command));
		});
		assertThrows(AdoptionException.class, () -> new BuildToolchainStep().execute(context, runner));
	}

	@Test
	void probesNothingForTheFallbackGuardWhichOnlyNeedsAShell(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new BuildToolchainStep().execute(context, runner);
		assertEquals(0, runner.count());
	}

	@Test
	void skipsWhenNoConfiguredBuildSystemMatches(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		BuildToolchainStep step = new BuildToolchainStep(List.of(new MavenBuildSystem(), new GradleBuildSystem()));
		assertDoesNotThrow(() -> step.execute(context, runner));
		assertEquals(0, runner.count());
	}

	@Test
	void probesTheToolTheVerificationWillActuallyRun(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "pom.xml", "<project/>");
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new BuildToolchainStep().execute(context, runner);
		assertEquals(new MavenBuildSystem().verifyCommand(context.repositoryDirectory()).get(0),
				runner.commandAt(0).get(0));
	}

	@Test
	void isNamedBuildToolchain() {
		assertEquals("build-toolchain", new BuildToolchainStep().name());
	}

	/**
	 * Most Gradle projects ship only the wrapper, so probing the PATH for a `gradle`
	 * aborted the adoption of a repository the host could build perfectly well.
	 */
	@Test
	void probesTheCheckoutsWrapperRatherThanThePathWhenOneIsShipped(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "build.gradle", "plugins { id 'java' }\n");
		AdoptionContexts.write(context, "gradlew", "#!/bin/sh\n");
		RecordingCommandRunner runner = new RecordingCommandRunner();
		Path wrapper = context.repositoryDirectory().resolve("gradlew").toAbsolutePath();

		new BuildToolchainStep(List.of(new GradleBuildSystem(new BuildWrapper("gradlew", "gradlew.bat", false))))
				.execute(context, runner);

		assertEquals(List.of(wrapper.toString(), "--version"), runner.commandAt(0));
	}

	/** A wrapper that cannot be run is named in the failure, not passed off as a missing PATH tool. */
	@Test
	void namesTheWrapperItCouldNotRun(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "build.gradle", "plugins { id 'java' }\n");
		AdoptionContexts.write(context, "gradlew", "#!/bin/sh\n");
		RecordingCommandRunner runner = RecordingCommandRunner.failing(126, "Permission denied");

		AdoptionException failure = assertThrows(AdoptionException.class,
				() -> new BuildToolchainStep(
						List.of(new GradleBuildSystem(new BuildWrapper("gradlew", "gradlew.bat", false))))
								.execute(context, runner));

		assertTrue(failure.getMessage().contains("gradlew"), failure.getMessage());
		assertTrue(failure.getMessage().contains("could not be run"), failure.getMessage());
	}
}

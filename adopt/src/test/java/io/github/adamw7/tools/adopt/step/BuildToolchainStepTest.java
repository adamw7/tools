package io.github.adamw7.tools.adopt.step;

import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertFailure(AdoptionException.class, () -> new BuildToolchainStep().execute(context, runner), "gradle");
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

	/**
	 * A checkout with no recognised build file falls to the guard that runs through a
	 * shell, so the shell is probed like any other build tool.
	 */
	@Test
	void probesTheShellTheFallbackGuardRunsThrough(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new BuildToolchainStep().execute(context, runner);
		assertEquals(List.of("sh", "-c", "exit 0"), runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
		assertEquals(1, runner.count());
	}

	/**
	 * A host with no shell on its PATH — a stock Windows one, where the {@code sh.exe}
	 * Git for Windows ships stays under its usr/bin unless the operator opted into the
	 * Unix tools — used to run all the way to {@link VerifyStep} before finding out.
	 */
	@Test
	void anAbsentShellAbortsTheAdoptionAtTheSecondStep(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner(command -> {
			throw new AdoptionException("Could not start command: " + String.join(" ", command));
		});

		assertFailure(AdoptionException.class, () -> new BuildToolchainStep().execute(context, runner),
				"build-toolchain failed", "sh");
	}

	/**
	 * The remedy comes from the build system, so a project with no build file is not
	 * told to "install github-actions on the PATH", which names nothing installable.
	 */
	@Test
	void theFallbacksFailureAdvisesAboutAShellRatherThanAboutGitHubActions(@TempDir Path workspace)
			throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = RecordingCommandRunner.failing(127, "sh: not found");

		AdoptionException failure = assertThrows(AdoptionException.class,
				() -> new BuildToolchainStep().execute(context, runner));

		assertFalse(failure.getMessage().contains("Install github-actions"), failure.getMessage());
		assertTrue(failure.getMessage().contains("POSIX sh"), failure.getMessage());
	}

	/** A shell that answers the probe lets the adoption carry on. */
	@Test
	void aShellThatRunsLetsTheFallbackAdoptionProceed(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		assertDoesNotThrow(() -> new BuildToolchainStep().execute(context, runner));
	}

	/** A Maven checkout is still told to install Maven, not to find a shell. */
	@Test
	void aBuildToolsFailureStillAdvisesAboutThatBuildTool(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "pom.xml", "<project/>");
		RecordingCommandRunner runner = RecordingCommandRunner.failing(127, "mvn: not found");

		assertFailure(AdoptionException.class, () -> new BuildToolchainStep().execute(context, runner),
				"Install maven on the PATH");
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
		Path wrapper = executableWrapper(context);
		RecordingCommandRunner runner = new RecordingCommandRunner();

		new BuildToolchainStep(List.of(new GradleBuildSystem(new BuildWrapper("gradlew", "gradlew.bat", false))))
				.execute(context, runner);

		assertEquals(List.of(wrapper.toString(), "--version"), runner.commandAt(0));
	}

	/**
	 * A wrapper the project committed without its executable bit is probed through sh,
	 * exactly as the verification will launch it. Probing it as a program instead
	 * failed with "Permission denied" and aborted the adoption of a repository whose
	 * build was fine — the usual state of a wrapper added from Windows.
	 */
	@Test
	void probesAWrapperWithoutItsExecutableBitThroughTheShell(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "build.gradle", "plugins { id 'java' }\n");
		AdoptionContexts.write(context, "gradlew", "#!/bin/sh\n");
		RecordingCommandRunner runner = new RecordingCommandRunner();
		Path wrapper = context.repositoryDirectory().resolve("gradlew").toAbsolutePath();

		new BuildToolchainStep(List.of(new GradleBuildSystem(withoutTheExecutableBit()))).execute(context, runner);

		assertEquals(List.of("sh", wrapper.toString(), "--version"), runner.commandAt(0));
	}

	/**
	 * A POSIX host whose wrapper carries no executable bit. Windows has none to leave
	 * off, so writing the file without it describes the case only on Linux and macOS
	 * and the bit has to be answered for rather than read off the filesystem.
	 */
	private BuildWrapper withoutTheExecutableBit() {
		return new BuildWrapper("gradlew", "gradlew.bat", false, wrapper -> false);
	}

	private Path executableWrapper(AdoptionContext context) throws IOException {
		AdoptionContexts.write(context, "gradlew", "#!/bin/sh\n");
		Path wrapper = context.repositoryDirectory().resolve("gradlew").toAbsolutePath();
		assertTrue(wrapper.toFile().setExecutable(true), "could not make " + wrapper + " executable");
		return wrapper;
	}

	/** A wrapper that cannot be run is named in the failure, not passed off as a missing PATH tool. */
	@Test
	void namesTheWrapperItCouldNotRun(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "build.gradle", "plugins { id 'java' }\n");
		AdoptionContexts.write(context, "gradlew", "#!/bin/sh\n");
		RecordingCommandRunner runner = RecordingCommandRunner.failing(126, "Permission denied");

		assertFailure(AdoptionException.class,
				() -> new BuildToolchainStep(
						List.of(new GradleBuildSystem(new BuildWrapper("gradlew", "gradlew.bat", false))))
								.execute(context, runner),
				"gradlew", "could not be run");
	}
}

package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class VerifyStepTest {

	private AdoptionContext context(Path workspace) throws IOException {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/demo.git", workspace);
		Files.createDirectories(context.repositoryDirectory());
		return context;
	}

	private void writePom(AdoptionContext context) throws IOException {
		Files.writeString(context.repositoryDirectory().resolve("pom.xml"), "<project/>");
	}

	@Test
	void runsMavenValidateInCheckout(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		writePom(context);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new VerifyStep().execute(context, runner);
		assertEquals(MavenBuildSystem.VERIFY_COMMAND, runner.commandAt(0));
		assertEquals(context.repositoryDirectory(), runner.invocations().get(0).workingDirectory());
	}

	@Test
	void runsGradleGuardTaskForAGradleCheckout(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		Files.writeString(context.repositoryDirectory().resolve("build.gradle"), "plugins { id 'java' }\n");
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new VerifyStep().execute(context, runner);
		assertEquals(GradleBuildSystem.VERIFY_COMMAND, runner.commandAt(0));
	}

	@Test
	void runsTheDetectedBuildSystemsVerifyCommand(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		writePom(context);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		BuildSystem custom = new FakeBuildSystem(List.of("just", "verify"));
		new VerifyStep(List.of(custom)).execute(context, runner);
		assertEquals(List.of("just", "verify"), runner.commandAt(0));
	}

	@Test
	void failedVerificationAbortsAdoption(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		writePom(context);
		RecordingCommandRunner runner = new RecordingCommandRunner(
				command -> new CommandResult(command, 1, "CLAUDE.md is malformed"));
		assertThrows(AdoptionException.class, () -> new VerifyStep().execute(context, runner));
	}

	@Test
	void skipsWhenNoSupportedBuildFileIsPresent(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		RecordingCommandRunner runner = new RecordingCommandRunner();
		new VerifyStep().execute(context, runner);
		assertEquals(0, runner.count());
	}

	@Test
	void isNamedVerify() {
		assertEquals("verify", new VerifyStep().name());
	}

	private static final class FakeBuildSystem implements BuildSystem {

		private final List<String> verifyCommand;

		private FakeBuildSystem(List<String> verifyCommand) {
			this.verifyCommand = verifyCommand;
		}

		@Override
		public String name() {
			return "fake";
		}

		@Override
		public boolean matches(Path repositoryDirectory) {
			return true;
		}

		@Override
		public boolean install(Path repositoryDirectory) {
			return true;
		}

		@Override
		public List<String> verifyCommand() {
			return verifyCommand;
		}
	}
}

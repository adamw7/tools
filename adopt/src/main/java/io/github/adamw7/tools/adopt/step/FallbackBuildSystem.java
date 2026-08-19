package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The catch-all build system for the adoption: it matches every checkout, so a
 * repository with no Maven or Gradle build file still gets a {@code CLAUDE.md}
 * guard. Because it matches unconditionally it is listed last in
 * {@link BuildSystems#DEFAULTS}, and only wins when no real build tool detected a
 * build file.
 *
 * <p>The guard is a GitHub Actions workflow and the portable shell script it runs,
 * installed with {@link WorkflowGuardInstaller}. The verification runs that same
 * script locally, so a missing or empty {@code CLAUDE.md} fails the adoption before
 * the branch is pushed, exactly as it would in CI.
 */
public class FallbackBuildSystem implements BuildSystem {

	/** The shell the guard script is run with, here and in the workflow that runs it in CI. */
	static final String SHELL = "sh";

	static final List<String> VERIFY_COMMAND = List.of(SHELL, WorkflowGuardInstaller.SCRIPT_FILE);

	/**
	 * Asks the shell to do nothing and exit zero, rather than for its version:
	 * {@code sh --version} is not portable and exits non-zero under {@code dash}, so
	 * probing with it would report a perfectly good shell as unusable. Every POSIX
	 * shell answers this one.
	 */
	static final List<String> SHELL_PROBE = List.of(SHELL, "-c", "exit 0");

	private final WorkflowGuardInstaller installer = new WorkflowGuardInstaller();

	@Override
	public String name() {
		return "github-actions";
	}

	@Override
	public boolean matches(Path repositoryDirectory) {
		return true;
	}

	@Override
	public List<String> writtenPaths() {
		return List.of(WorkflowGuardInstaller.WORKFLOW_FILE, WorkflowGuardInstaller.SCRIPT_FILE);
	}

	@Override
	public boolean install(Path repositoryDirectory) {
		return installer.install(repositoryDirectory);
	}

	@Override
	public boolean isGuardInstalled(Path repositoryDirectory) {
		return installer.isInstalled(repositoryDirectory);
	}

	@Override
	public List<String> verifyCommand(Path repositoryDirectory) {
		return VERIFY_COMMAND;
	}

	/**
	 * The guard runs through {@code sh}, so that is what is probed. Answering "nothing
	 * to check" instead assumed every host has a shell on its {@code PATH}, which a
	 * Windows one generally has not, so the adoption ran to {@link VerifyStep} — past
	 * the clone, the {@code claude init}, and both commits — before failing on a shell
	 * it could have missed at its second step.
	 */
	@Override
	public Optional<List<String>> toolProbe(Path repositoryDirectory) {
		return Optional.of(SHELL_PROBE);
	}

	/** Nothing here is installable as "github-actions"; what is missing is a shell. */
	@Override
	public String toolAdvice() {
		return "Put a POSIX " + SHELL + " on the PATH — on Windows, the one Git for Windows installs under"
				+ " its usr/bin — or give the project a Maven or Gradle build file to wire the guard into"
				+ " instead.";
	}
}

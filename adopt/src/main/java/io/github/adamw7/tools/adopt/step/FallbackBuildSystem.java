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
 * installed with {@link WorkflowGuardInstaller}; the verification runs that same
 * script locally, so a missing or empty {@code CLAUDE.md} fails the adoption
 * before the branch is pushed, exactly as it would in CI afterwards.
 */
public class FallbackBuildSystem implements BuildSystem {

	static final List<String> VERIFY_COMMAND = List.of("sh", WorkflowGuardInstaller.SCRIPT_FILE);

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
	public boolean install(Path repositoryDirectory) {
		return installer.install(repositoryDirectory);
	}

	@Override
	public List<String> verifyCommand(Path repositoryDirectory) {
		return VERIFY_COMMAND;
	}

	/**
	 * The guard runs through {@code sh}, which every platform the adoption supports
	 * already provides and which has no portable {@code --version} probe, so there is
	 * nothing to check ahead of time.
	 */
	@Override
	public Optional<List<String>> toolProbe(Path repositoryDirectory) {
		return Optional.empty();
	}
}

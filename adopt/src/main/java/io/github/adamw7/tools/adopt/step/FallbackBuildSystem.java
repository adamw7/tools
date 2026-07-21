package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

/**
 * The catch-all build system for the adoption: it matches every checkout, so a
 * repository with no Maven or Gradle build file still gets a {@code CLAUDE.md}
 * guard instead of being adopted with none. Because it matches unconditionally it
 * is listed last in {@link BuildSystems#DEFAULTS}, after the real build tools, so
 * it only wins when none of them detected a build file.
 *
 * <p>The guard is a GitHub Actions workflow and the portable shell script it runs,
 * installed with {@link WorkflowGuardInstaller}; the verification runs that same
 * script locally so a missing or empty {@code CLAUDE.md} fails the adoption before
 * the branch is pushed, exactly as it would in CI afterwards.
 */
public class FallbackBuildSystem implements BuildSystem {

	static final List<String> VERIFY_COMMAND = List.of("sh", WorkflowGuardInstaller.SCRIPT_FILE);

	private final WorkflowGuardInstaller installer;

	public FallbackBuildSystem() {
		this(new WorkflowGuardInstaller());
	}

	public FallbackBuildSystem(WorkflowGuardInstaller installer) {
		this.installer = installer;
	}

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
	public List<String> verifyCommand() {
		return VERIFY_COMMAND;
	}
}

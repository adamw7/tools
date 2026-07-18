package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Maven support for the adoption: detects a {@code pom.xml}, wires the
 * {@code claude-code-enforcer} rule into it with {@link PomEnforcerInstaller},
 * and verifies the rule with a non-recursive {@code mvn -N validate} so the
 * freshly generated {@code CLAUDE.md} is validated against the full format rule
 * before the branch is pushed.
 */
public class MavenBuildSystem implements BuildSystem {

	static final List<String> VERIFY_COMMAND = List.of("mvn", "-q", "-N", "validate");

	private static final String POM = "pom.xml";

	private final PomEnforcerInstaller installer;

	public MavenBuildSystem() {
		this(new PomEnforcerInstaller());
	}

	public MavenBuildSystem(PomEnforcerInstaller installer) {
		this.installer = installer;
	}

	@Override
	public String name() {
		return "maven";
	}

	@Override
	public boolean matches(Path repositoryDirectory) {
		return Files.isRegularFile(repositoryDirectory.resolve(POM));
	}

	@Override
	public boolean install(Path repositoryDirectory) {
		return installer.install(repositoryDirectory.resolve(POM));
	}

	@Override
	public List<String> verifyCommand() {
		return VERIFY_COMMAND;
	}
}

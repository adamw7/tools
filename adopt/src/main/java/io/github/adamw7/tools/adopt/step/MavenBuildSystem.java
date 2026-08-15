package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Maven support for the adoption: detects a {@code pom.xml}, wires the
 * {@code claude-code-enforcer} rule into it with {@link PomEnforcerInstaller},
 * and verifies the rule with a non-recursive {@code mvn -N validate} so the
 * freshly generated {@code CLAUDE.md} is validated against the full format rule
 * before the branch is pushed.
 *
 * <p>A checkout that ships an {@code mvnw} is verified with that wrapper rather
 * than with a {@code mvn} off the {@code PATH}, so the guard runs under the Maven
 * version the project pinned and a host with no Maven installed can still adopt
 * it. See {@link AbstractWrappedBuildSystem}.
 */
public class MavenBuildSystem extends AbstractWrappedBuildSystem {

	private static final String NAME = "maven";
	private static final String MAVEN = "mvn";
	private static final List<String> VERIFY_ARGUMENTS = List.of("-q", "-N", "validate");

	static final String POM = "pom.xml";

	private final PomEnforcerInstaller installer;

	public MavenBuildSystem() {
		this(Optional.empty());
	}

	/**
	 * @param ruleVersion the released {@code claude-code-enforcer} version to pin into
	 *                    the adopted POM, or empty to resolve the version of the
	 *                    {@code tools} build running the adoption
	 */
	public MavenBuildSystem(Optional<String> ruleVersion) {
		this(PomEnforcerInstaller.pinning(ruleVersion));
	}

	public MavenBuildSystem(PomEnforcerInstaller installer) {
		this(installer, new BuildWrapper("mvnw", "mvnw.cmd"));
	}

	MavenBuildSystem(PomEnforcerInstaller installer, BuildWrapper wrapper) {
		super(NAME, MAVEN, VERIFY_ARGUMENTS, wrapper);
		this.installer = installer;
	}

	@Override
	public boolean matches(Path repositoryDirectory) {
		return Files.isRegularFile(repositoryDirectory.resolve(POM));
	}

	@Override
	public List<String> writtenPaths() {
		return List.of(POM);
	}

	@Override
	public boolean install(Path repositoryDirectory) {
		return installer.install(repositoryDirectory.resolve(POM));
	}

	/**
	 * The only build system that demands sections, because it is the only one wiring
	 * in the {@code claudeMdFormat} rule — the guard {@link #verifyCommand(Path)}
	 * then runs, and the one that fails a {@code CLAUDE.md} for a missing section
	 * rather than merely for being absent or blank. A Maven checkout is a JVM project,
	 * so the rule's Java and Maven sections are ones it can answer.
	 */
	@Override
	public List<String> requiredClaudeMdSections() {
		return ClaudeMdConformer.REQUIRED_SECTIONS;
	}
}

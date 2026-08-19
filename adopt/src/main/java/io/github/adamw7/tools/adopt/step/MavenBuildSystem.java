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
	private final GuardOptions guard;

	public MavenBuildSystem() {
		this(GuardOptions.defaults());
	}

	/**
	 * @param guard what the wired guard is made of: the rule set, the pinned rule
	 *              version, and the {@code CLAUDE.md} sections it demands
	 */
	public MavenBuildSystem(GuardOptions guard) {
		this(PomEnforcerInstaller.from(guard), guard, defaultWrapper());
	}

	public MavenBuildSystem(PomEnforcerInstaller installer) {
		this(installer, GuardOptions.defaults(), defaultWrapper());
	}

	MavenBuildSystem(PomEnforcerInstaller installer, BuildWrapper wrapper) {
		this(installer, GuardOptions.defaults(), wrapper);
	}

	MavenBuildSystem(PomEnforcerInstaller installer, GuardOptions guard, BuildWrapper wrapper) {
		super(NAME, MAVEN, VERIFY_ARGUMENTS, wrapper);
		this.installer = installer;
		this.guard = guard;
	}

	private static BuildWrapper defaultWrapper() {
		return new BuildWrapper("mvnw", "mvnw.cmd");
	}

	@Override
	public boolean matches(Path repositoryDirectory) {
		return Files.isRegularFile(repositoryDirectory.resolve(POM));
	}

	@Override
	public List<String> writtenPaths() {
		return List.of(POM);
	}

	/**
	 * The sections the guard is given are the ones {@link #requiredClaudeMdSections()}
	 * answers, which is what the conformer has just reshaped the document to. Reading
	 * them from the one accessor is what keeps the document and the guard beside it
	 * making the same demand.
	 */
	@Override
	public boolean install(Path repositoryDirectory) {
		return installer.install(repositoryDirectory.resolve(POM), requiredClaudeMdSections());
	}

	/**
	 * The only build system that demands sections, because it is the only one wiring
	 * in a rule that reads them — the guard {@link #verifyCommand(Path)} then runs,
	 * and the one that fails a {@code CLAUDE.md} for a missing section rather than
	 * merely for being absent or blank.
	 *
	 * <p>The default is this repository's own list, on the reasoning that a Maven
	 * checkout is a JVM project and can answer Java and Maven questions. That is a
	 * default and not a fact: a Maven build whose {@code CLAUDE.md} is about something
	 * else names its own sections through {@link GuardOptions}, and they are written
	 * into the guard as well as conformed to.
	 */
	@Override
	public List<String> requiredClaudeMdSections() {
		return guard.sectionsOr(ClaudeMdConformer.REQUIRED_SECTIONS);
	}
}

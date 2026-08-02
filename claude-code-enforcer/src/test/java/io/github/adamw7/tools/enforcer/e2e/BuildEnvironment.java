package io.github.adamw7.tools.enforcer.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Where the outer build put the things an end-to-end test needs: the Maven that
 * is running, the local repository it resolves from, and the rule jar plus
 * flattened pom this module has just produced. None of that can be worked out
 * from inside a forked test JVM — the local repository may have been relocated
 * with {@code -Dmaven.repo.local}, and the jar's name comes from the build's
 * {@code finalName} — so the {@code integration-tests} profile passes each one in
 * as a system property and this class reads them.
 * <p>
 * A missing property means the {@code *IT}s were run outside that profile, which
 * is a build-setup mistake rather than a failed expectation, so it is reported as
 * an {@link IllegalStateException} naming the property.
 */
final class BuildEnvironment {

	private static final String MAVEN_HOME = "enforcer.it.mavenHome";
	private static final String LOCAL_REPOSITORY = "enforcer.it.localRepository";
	private static final String VERSION = "enforcer.it.version";
	private static final String JAR = "enforcer.it.jar";
	private static final String POM = "enforcer.it.pom";
	private static final String REPOSITORY_ROOT = "enforcer.it.repositoryRoot";

	static final String GROUP_ID = "io.github.adamw7";
	static final String ARTIFACT_ID = "tools.claude-code-enforcer";

	private final Path mavenExecutable;
	private final Path localRepository;
	private final String version;
	private final Path jar;
	private final Path pom;
	private final Path repositoryRoot;

	BuildEnvironment() {
		this.mavenExecutable = mavenExecutableIn(directory(MAVEN_HOME));
		this.localRepository = directory(LOCAL_REPOSITORY);
		this.version = property(VERSION);
		this.jar = file(JAR);
		this.pom = file(POM);
		this.repositoryRoot = directory(REPOSITORY_ROOT);
	}

	Path mavenExecutable() {
		return mavenExecutable;
	}

	Path localRepository() {
		return localRepository;
	}

	/** The version of the rule jar under test, which every fixture pom depends on. */
	String version() {
		return version;
	}

	/** The jar this module's {@code package} phase produced, published before the first fixture build. */
	Path jar() {
		return jar;
	}

	/**
	 * The flattened pom that goes with {@link #jar()}. Installing the jar with it
	 * rather than with a generated pom is what gives a fixture build the rule's own
	 * dependencies — Jackson above all — on the enforcer plugin's class path.
	 */
	Path pom() {
		return pom;
	}

	/** The repository this module sits in, whose own configuration one of the {@code *IT}s enforces. */
	Path repositoryRoot() {
		return repositoryRoot;
	}

	/**
	 * The launcher rather than the wrapper script name alone, because a test starts
	 * a process directly instead of going through a shell that would resolve
	 * {@code mvn} on the {@code PATH}.
	 */
	private static Path mavenExecutableIn(Path mavenHome) {
		String launcher = isWindows() ? "mvn.cmd" : "mvn";
		Path executable = mavenHome.resolve("bin").resolve(launcher);
		if (!Files.isRegularFile(executable)) {
			throw new IllegalStateException("No Maven launcher at " + executable);
		}
		return executable;
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	private static Path file(String name) {
		Path path = Path.of(property(name));
		if (!Files.isRegularFile(path)) {
			throw new IllegalStateException("The " + name + " property does not name a file: " + path);
		}
		return path;
	}

	private static Path directory(String name) {
		Path path = Path.of(property(name));
		if (!Files.isDirectory(path)) {
			throw new IllegalStateException("The " + name + " property does not name a directory: " + path);
		}
		return path.toAbsolutePath().normalize();
	}

	private static String property(String name) {
		String value = System.getProperty(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("The " + name + " system property is not set; run the integration tests "
					+ "with the integration-tests profile, which passes it in");
		}
		return value;
	}
}

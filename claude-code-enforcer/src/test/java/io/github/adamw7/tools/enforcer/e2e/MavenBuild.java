package io.github.adamw7.tools.enforcer.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a real Maven build in a real process, which is the only way to observe an
 * enforcer rule the way a contributor meets it. A unit test constructs a rule and
 * calls {@code execute()} on it directly; everything between the pom and that call
 * — Maven resolving the rule jar, Sisu finding the class behind a {@code @Named}
 * element, and Plexus binding each configuration element to a field — is skipped,
 * and each of those is a way a rule can be perfectly correct and still never run.
 * <p>
 * Builds are launched from the Maven that is running the outer build and are
 * pointed at its local repository, so a fixture resolves the plugins and
 * dependencies already downloaded for this build rather than reaching for the
 * network. Standard output and error are merged into a temporary log outside the
 * project directory — a build must never leave a file in the repository it was
 * pointed at — and returned as the {@link BuildOutcome}.
 */
final class MavenBuild {

	/**
	 * Far above the second or two a fixture build takes, since it only has to fail a
	 * build that has stopped responding rather than one that is merely slow: the
	 * first run in a cold environment may still be downloading the install plugin.
	 */
	private static final long TIMEOUT_MINUTES = 5;

	private static final String INSTALL_PLUGIN = "org.apache.maven.plugins:maven-install-plugin";

	private static final LoggedProcess MAVEN = new LoggedProcess("enforcer-e2e", TIMEOUT_MINUTES);

	private final BuildEnvironment environment;

	MavenBuild(BuildEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Publishes the jar this module has just built into the local repository, so a
	 * fixture pom can name it as a plugin dependency. A maven-enforcer rule has to be
	 * resolvable as an artifact before the build that uses it starts, which is why the
	 * repository documents the doc check as a two-phase build; an {@code *IT} runs at
	 * {@code integration-test}, before this module is installed, so it publishes the
	 * jar itself rather than testing whatever an earlier build happened to leave
	 * behind.
	 *
	 * @param installPluginVersion the version the repository pins, rather than the
	 *                             one Maven's own super-pom defaults to: only the
	 *                             pinned one is certain to be in the local repository
	 *                             already, so taking it keeps this off the network
	 */
	void publishRuleUnderTest(String installPluginVersion) {
		Path workingDirectory = temporaryDirectory();
		BuildOutcome outcome = run(workingDirectory,
				INSTALL_PLUGIN + ":" + installPluginVersion + ":install-file",
				"-Dfile=" + environment.jar(),
				"-DpomFile=" + environment.pom());
		LoggedProcess.delete(workingDirectory);
		if (!outcome.succeeded()) {
			throw new IllegalStateException("Could not publish " + environment.jar() + ": " + outcome.describe());
		}
	}

	/**
	 * Runs Maven in {@code directory} with the given arguments. Batch mode keeps the
	 * log free of progress animations a test would have to parse around, and the
	 * explicit local repository makes the fixture build resolve from the same place
	 * as the build that started it.
	 */
	BuildOutcome run(Path directory, String... arguments) {
		List<String> command = new ArrayList<>(List.of(
				environment.mavenExecutable().toString(),
				"-B",
				"--no-transfer-progress",
				"-Dmaven.repo.local=" + environment.localRepository()));
		command.addAll(List.of(arguments));
		return MAVEN.run(directory, command);
	}

	/** A directory with no pom in it, so a project-free goal is not handed a project. */
	private Path temporaryDirectory() {
		try {
			return Files.createTempDirectory("enforcer-e2e");
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create a working directory", e);
		}
	}
}

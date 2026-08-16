package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * Resolves the {@code claude-code-enforcer} rule version that
 * {@link PomEnforcerInstaller} pins into an adopted project's {@code pom.xml}:
 * the version of the {@code tools} release running the adoption, refusing a
 * snapshot.
 *
 * <p>Where the version comes from is a question about this build's metadata, not
 * about editing XML, so it lives here rather than in the installer.
 */
final class EnforcerRuleVersion {

	static final String BUILD_PROPERTIES = "/adopt-build.properties";
	static final String RULE_VERSION_KEY = "enforcer.rule.version";
	private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

	private EnforcerRuleVersion() {
	}

	/**
	 * The released rule version to wire in. Resolving it lazily — only once a POM is
	 * actually being edited — keeps merely constructing the default
	 * {@link BuildSystems#DEFAULTS} list, or adopting a repository that builds with
	 * something other than Maven, from depending on the running build's version. It is
	 * re-read per repository rather than cached, so this class keeps the immutability
	 * every step here is held to.
	 */
	static String release() {
		return requireRelease(fromBuildMetadata());
	}

	/**
	 * A snapshot resolves from the adopter's own local repository and nowhere else,
	 * so wiring one in would open a pull request that builds here and fails for the
	 * adopted project's CI and every contributor — and {@link VerifyStep} could not
	 * catch it, resolving against the same local repository. Refusing here turns
	 * that into an immediate, explicable failure.
	 */
	static String requireRelease(String version) {
		if (version == null || version.isBlank()) {
			throw new AdoptionException("No " + RULE_VERSION_KEY + " to wire into the adopted project's pom.xml:"
					+ " a blank version would leave the build with a dependency it cannot resolve.");
		}
		if (version.endsWith(SNAPSHOT_SUFFIX)) {
			throw new AdoptionException("Refusing to wire the snapshot enforcer rule version " + version
					+ " into the adopted project's pom.xml: a snapshot is not resolvable outside this machine's"
					+ " local repository, so the pull request would break the adopted project's build."
					+ " Run the adoption from a released build of tools, or supply a released version to"
					+ " PomEnforcerInstaller.");
		}
		return version;
	}

	/**
	 * Reads the rule version from the metadata Maven filters into
	 * {@value #BUILD_PROPERTIES} at build time, so the dependency is pinned to the
	 * exact {@code tools} release running the adoption — resolvable from the
	 * repository that published it — rather than to a literal that silently drifts.
	 */
	static String fromBuildMetadata() {
		try (InputStream stream = EnforcerRuleVersion.class.getResourceAsStream(BUILD_PROPERTIES)) {
			return read(stream);
		} catch (IOException e) {
			throw new AdoptionException("Could not read build metadata: " + BUILD_PROPERTIES, e);
		}
	}

	/**
	 * Refuses metadata that reached the classpath unfiltered as firmly as metadata
	 * that is missing: an unsubstituted token would otherwise be wired into the
	 * adopted POM as if it were a version. Both delimiters are rejected — the
	 * resource is written with {@code @...@}, which is what this build filters, and
	 * {@code ${...}} is what it would carry had that configuration changed.
	 *
	 * <p>Package-visible because the stream is the only seam these refusals have.
	 * {@link #fromBuildMetadata()} reads one resource off this class's own
	 * classpath, and that resource is correct in every build that runs these tests —
	 * so a test driving the version through it can only ever take the path that
	 * succeeds, leaving the three failures this method words for nobody to reach.
	 * They are the failures worth reaching: each one is what stands between a broken
	 * build of {@code tools} and a literal {@code @enforcer.rule.version@} wired
	 * into a stranger's {@code pom.xml} by a pull request the adoption opened.
	 */
	static String read(InputStream stream) throws IOException {
		if (stream == null) {
			throw new AdoptionException("Build metadata not on the classpath: " + BUILD_PROPERTIES
					+ " (build the module so its resources are filtered)");
		}
		Properties properties = new Properties();
		properties.load(stream);
		String version = properties.getProperty(RULE_VERSION_KEY, "").strip();
		if (version.isEmpty() || version.startsWith("@") || version.startsWith("${")) {
			throw new AdoptionException(
					RULE_VERSION_KEY + " was not filtered into " + BUILD_PROPERTIES + " (found: '" + version + "')");
		}
		return version;
	}
}

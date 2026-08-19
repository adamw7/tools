package io.github.adamw7.tools.adopt.step;

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

	static final String RULE_VERSION_KEY = "enforcer.rule.version";
	private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

	private EnforcerRuleVersion() {
	}

	/**
	 * The released rule version to wire in, resolved lazily — only once a POM is being
	 * edited — so constructing {@link BuildSystems#DEFAULTS} or adopting a non-Maven
	 * repository does not depend on the running build's version. Re-read per
	 * repository rather than cached, keeping the immutability every step is held to.
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
	 * {@value BuildMetadata#BUILD_PROPERTIES} at build time, so the dependency is
	 * pinned to the exact {@code tools} release running the adoption — resolvable from
	 * the repository that published it — rather than to a literal that silently
	 * drifts.
	 */
	static String fromBuildMetadata() {
		return BuildMetadata.value(RULE_VERSION_KEY, "the claude-code-enforcer rule version to wire in");
	}
}

package io.github.adamw7.tools.adopt.step;

import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionException;

class EnforcerRuleVersionTest {

	/**
	 * The rule must be pinned to the version Maven filtered into
	 * {@code adopt-build.properties} — the release actually running the adoption —
	 * rather than a hardcoded literal that drifts as the project is versioned.
	 */
	@Test
	void readsTheFilteredBuildVersion() throws IOException {
		assertEquals(filteredRuleVersion(), EnforcerRuleVersion.fromBuildMetadata());
	}

	/**
	 * A snapshot resolves from the adopter's local repository and nowhere else, so
	 * wiring one in would open a pull request that only builds on the machine that
	 * opened it.
	 */
	@Test
	void aSnapshotRuleVersionIsRefused() {
		assertFailure(AdoptionException.class, () -> EnforcerRuleVersion.requireRelease("2.6.0-SNAPSHOT"),
				"2.6.0-SNAPSHOT");
	}

	@Test
	void aReleaseRuleVersionIsAccepted() {
		assertEquals("2.6.0", EnforcerRuleVersion.requireRelease("2.6.0"));
	}

	/**
	 * The version the default installer wires in must go through the release guard,
	 * so a snapshot build cannot put an unresolvable dependency into an adopted POM.
	 * Whether that refuses or returns depends on how this module was built, so the
	 * assertion pins the composition rather than one fixed outcome.
	 */
	@Test
	void theDefaultVersionGoesThroughTheReleaseGuard() {
		assertEquals(outcomeOf(() -> EnforcerRuleVersion.requireRelease(EnforcerRuleVersion.fromBuildMetadata())),
				outcomeOf(EnforcerRuleVersion::release));
	}

	/**
	 * The metadata is filtered into the jar by this module's own build, so a run
	 * whose classpath has no such resource is one assembled by something other than
	 * that build — a repackaged jar, a shaded one, a classloader that never saw the
	 * resources. Saying so beats the {@link NullPointerException} an unchecked read
	 * would raise several frames away from the cause.
	 */
	@Test
	void metadataMissingFromTheClasspathIsRefused() {
		assertFailure(AdoptionException.class, () -> BuildMetadata.read(null, EnforcerRuleVersion.RULE_VERSION_KEY, "the rule version"),
				BuildMetadata.BUILD_PROPERTIES);
	}

	/**
	 * The delimiter this build actually writes. An unsubstituted token reaching the
	 * adopted POM would be a dependency no repository can resolve, and it would get
	 * there through a pull request opened on somebody else's project.
	 */
	@Test
	void anUnfilteredAtToken() {
		assertUnfiltered("@enforcer.rule.version@");
	}

	/** The delimiter the resource would carry had this build's filtering been reconfigured. */
	@Test
	void anUnfilteredPropertyReference() {
		assertUnfiltered("${enforcer.rule.version}");
	}

	/** A resource carrying the key with nothing behind it names no version at all. */
	@Test
	void aBlankVersionIsRefused() {
		assertUnfiltered("   ");
	}

	/** A resource that was filtered but carries some other key answers no version either. */
	@Test
	void metadataWithoutTheVersionKeyIsRefused() throws IOException {
		assertFailure(AdoptionException.class, () -> BuildMetadata.read(metadata("some.other.key=2.6.0"), EnforcerRuleVersion.RULE_VERSION_KEY,
						"the rule version"),
				EnforcerRuleVersion.RULE_VERSION_KEY);
	}

	/**
	 * The surrounding whitespace a properties file carries is not part of the
	 * version, and wiring it into the POM would pin a dependency on a version string
	 * no repository holds.
	 */
	@Test
	void aFilteredVersionIsReadWithoutItsSurroundingWhitespace() throws IOException {
		assertEquals("2.6.0", BuildMetadata.read(metadata(EnforcerRuleVersion.RULE_VERSION_KEY + "=  2.6.0  "),
				EnforcerRuleVersion.RULE_VERSION_KEY, "the rule version"));
	}

	private void assertUnfiltered(String version) {
		assertFailure(AdoptionException.class,
				() -> BuildMetadata.read(metadata(EnforcerRuleVersion.RULE_VERSION_KEY + "=" + version),
						EnforcerRuleVersion.RULE_VERSION_KEY, "the rule version"),
				BuildMetadata.BUILD_PROPERTIES);
	}

	private InputStream metadata(String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	private String outcomeOf(Supplier<String> version) {
		try {
			return "wired " + version.get();
		} catch (AdoptionException e) {
			return "refused: " + e.getMessage();
		}
	}

	private String filteredRuleVersion() throws IOException {
		try (InputStream stream = getClass().getResourceAsStream(BuildMetadata.BUILD_PROPERTIES)) {
			assertNotNull(stream, BuildMetadata.BUILD_PROPERTIES + " must be filtered onto the test classpath");
			Properties properties = new Properties();
			properties.load(stream);
			String version = properties.getProperty(EnforcerRuleVersion.RULE_VERSION_KEY, "").strip();
			assertFalse(version.isEmpty() || version.startsWith("${"),
					"enforcer.rule.version must be filtered to a concrete version, was: " + version);
			return version;
		}
	}
}

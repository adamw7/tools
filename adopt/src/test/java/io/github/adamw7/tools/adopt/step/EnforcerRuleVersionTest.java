package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
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
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> EnforcerRuleVersion.requireRelease("2.6.0-SNAPSHOT"));
		assertTrue(thrown.getMessage().contains("2.6.0-SNAPSHOT"), thrown.getMessage());
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

	private String outcomeOf(Supplier<String> version) {
		try {
			return "wired " + version.get();
		} catch (AdoptionException e) {
			return "refused: " + e.getMessage();
		}
	}

	private String filteredRuleVersion() throws IOException {
		try (InputStream stream = getClass().getResourceAsStream(EnforcerRuleVersion.BUILD_PROPERTIES)) {
			assertNotNull(stream, EnforcerRuleVersion.BUILD_PROPERTIES + " must be filtered onto the test classpath");
			Properties properties = new Properties();
			properties.load(stream);
			String version = properties.getProperty(EnforcerRuleVersion.RULE_VERSION_KEY, "").strip();
			assertFalse(version.isEmpty() || version.startsWith("${"),
					"enforcer.rule.version must be filtered to a concrete version, was: " + version);
			return version;
		}
	}
}

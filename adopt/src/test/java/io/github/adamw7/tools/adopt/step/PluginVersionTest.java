package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PluginVersionTest {

	private static final String MINIMUM = "3.1.0";

	@Test
	void readsAReleaseBelowTheMinimumAsBelowIt() {
		assertBelow(List.of("3.0.0", "3.0.5", "2.9.9", "1", "3.0"));
	}

	@Test
	void readsTheMinimumAndEveryLaterReleaseAsNotBelowIt() {
		assertNotBelow(List.of("3.1.0", "3.1.1", "3.2", "3.6.3", "4.0.0", "10.0.0"));
	}

	/**
	 * A component the shorter version does not carry counts as zero, so the two name
	 * one release rather than the shorter being the older.
	 */
	@Test
	void readsAMissingTrailingComponentAsZero() {
		assertNotBelow(List.of("3.1"));
		assertBelow(List.of("3.0"));
	}

	/**
	 * Only the numeric prefix is compared, which is the safe direction: a milestone of
	 * the minimum reads as that release and is not refused, while a milestone of an
	 * older one still is.
	 */
	@Test
	void comparesOnlyTheNumbersBeforeAQualifier() {
		assertBelow(List.of("3.0.0-M3"));
		assertNotBelow(List.of("3.1.0-M1", "3.2.1-SNAPSHOT"));
	}

	/**
	 * A literal naming no number at all cannot be judged from the POM in hand — the
	 * version is a property, or comes from a parent — and answering "below" would
	 * refuse an ordinary project over a version nobody can read here. The last of
	 * these is longer than any version anybody writes, which is read as no number
	 * rather than overflowed into one.
	 */
	@Test
	void readsAVersionThatNamesNoNumberAsNotBelowAnything() {
		assertNotBelow(List.of("", "   ", "${enforcer.version}", "RELEASE", "9999999999"));
	}

	private void assertBelow(List<String> versions) {
		versions.forEach(version -> assertTrue(PluginVersion.isBelow(version, MINIMUM), version));
	}

	private void assertNotBelow(List<String> versions) {
		versions.forEach(version -> assertFalse(PluginVersion.isBelow(version, MINIMUM), version));
	}
}

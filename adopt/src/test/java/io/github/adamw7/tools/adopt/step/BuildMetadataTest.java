package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * The two versions the adoption writes into somebody else's POM, and the refusals
 * that stand between a broken build of this project and a literal
 * {@code @enforcer.rule.version@} wired into a stranger's build.
 */
class BuildMetadataTest {

	/**
	 * The defect this mechanism exists to prevent, now covering the plugin version
	 * too: the version the adoption pins and the version this project builds against
	 * must be one number, so that moving one moves the other.
	 */
	@Test
	void thePluginVersionItPinsIsTheOneTheRootPomManages() throws IOException {
		String managed = versionManagedByTheRootPom();

		assertEquals(managed, PomEnforcerInstaller.enforcerVersion(),
				"the maven-enforcer-plugin version wired into an adopted POM has drifted from the root pom's");
	}

	@Test
	void readsThePluginVersion() {
		assertTrue(PomEnforcerInstaller.enforcerVersion().matches("\\d+\\.\\d+\\.\\d+"),
				PomEnforcerInstaller.enforcerVersion());
	}

	/** A failure has to say which key is missing and what the value was for. */
	@Test
	void failureNamesTheKeyAndWhatItIsFor() {
		AdoptionException thrown = assertThrows(AdoptionException.class,
				() -> BuildMetadata.read(properties("a.key="), "a.key", "the version to pin"));

		assertTrue(thrown.getMessage().contains("a.key"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("the version to pin"), thrown.getMessage());
	}

	private InputStream properties(String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Read from the root pom itself rather than from a second copy of the number,
	 * because a copy is exactly what this test exists to stop.
	 */
	private String versionManagedByTheRootPom() throws IOException {
		String pom = Files.readString(rootPom());
		String property = "<maven-enforcer-plugin.version>";
		int start = pom.indexOf(property) + property.length();
		return pom.substring(start, pom.indexOf('<', start));
	}

	/**
	 * Surefire runs a module's tests with the module directory as the working
	 * directory, so the reactor root is its parent.
	 */
	private Path rootPom() {
		return Path.of("..", "pom.xml").toAbsolutePath().normalize();
	}
}

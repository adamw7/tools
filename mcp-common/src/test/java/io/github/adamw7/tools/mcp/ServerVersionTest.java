package io.github.adamw7.tools.mcp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

public class ServerVersionTest {

	/**
	 * The handshake must advertise the version Maven filtered into
	 * {@code mcp-common-build.properties} — the build the server was packaged from —
	 * rather than a literal in the source unrelated to the project's revision.
	 */
	@Test
	void readsTheFilteredBuildVersion() throws IOException {
		assertEquals(filteredVersion(), ServerVersion.fromBuildMetadata());
	}

	@Test
	void theFilteredBuildVersionIsAConcreteVersion() throws IOException {
		assertNotEquals(ServerVersion.UNKNOWN, filteredVersion());
	}

	/**
	 * Resolved once, so every transport advertises the same answer as a direct read.
	 */
	@Test
	void currentIsTheVersionFromTheBuildMetadata() {
		assertEquals(ServerVersion.fromBuildMetadata(), ServerVersion.current());
	}

	@Test
	void metadataMissingFromTheClasspathFallsBackToUnknown() throws IOException {
		assertEquals(ServerVersion.UNKNOWN, ServerVersion.read(null));
	}

	/**
	 * An unsubstituted token would otherwise be advertised verbatim, telling a client
	 * it is talking to a server named after the filtering expression.
	 */
	@Test
	void anUnfilteredTokenFallsBackToUnknown() throws IOException {
		assertEquals(ServerVersion.UNKNOWN, versionIn("mcp.server.version=@project.version@"));
	}

	@Test
	void anUnresolvedPropertyReferenceFallsBackToUnknown() throws IOException {
		assertEquals(ServerVersion.UNKNOWN, versionIn("mcp.server.version=${project.version}"));
	}

	@Test
	void aMissingKeyFallsBackToUnknown() throws IOException {
		assertEquals(ServerVersion.UNKNOWN, versionIn("some.other.key=2.6.0"));
	}

	@Test
	void aBlankVersionFallsBackToUnknown() throws IOException {
		assertEquals(ServerVersion.UNKNOWN, versionIn("mcp.server.version=   "));
	}

	@Test
	void aFilteredVersionIsReadAsIs() throws IOException {
		assertEquals("2.6.0", versionIn("mcp.server.version=2.6.0"));
	}

	private String versionIn(String metadata) throws IOException {
		return ServerVersion.read(new ByteArrayInputStream(metadata.getBytes(UTF_8)));
	}

	private String filteredVersion() throws IOException {
		try (InputStream stream = getClass().getResourceAsStream(ServerVersion.BUILD_PROPERTIES)) {
			assertNotNull(stream, ServerVersion.BUILD_PROPERTIES + " must be filtered onto the test classpath");
			Properties properties = new Properties();
			properties.load(stream);
			String version = properties.getProperty(ServerVersion.VERSION_KEY, "").strip();
			assertFalse(version.isEmpty() || version.startsWith("@") || version.startsWith("${"),
					ServerVersion.VERSION_KEY + " must be filtered to a concrete version, was: " + version);
			return version;
		}
	}
}

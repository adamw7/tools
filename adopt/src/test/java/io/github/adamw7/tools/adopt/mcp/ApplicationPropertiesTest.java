package io.github.adamw7.tools.adopt.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * The configuration the server jar ships, read off the classpath rather than out of a
 * booted context: the file is what an operator gets by default, and asserting it costs
 * neither a web server nor a port.
 *
 * <p>The binding is the reason this test exists. {@code adopt_repo} clones repositories,
 * runs {@code claude}, wires a guard into a build file, pushes a branch and opens a pull
 * request — all on the host's own {@code git} and {@code gh} credentials — and the
 * {@code /mcp} endpoint has no authentication in front of it. Left unset,
 * {@code server.address} has Spring Boot bind the HTTP transports to every interface,
 * which offers that reach to anything that can route to the host.
 */
class ApplicationPropertiesTest {

	private static final String RESOURCE = "/application.properties";

	@Test
	void bindsTheHttpTransportsToLoopbackOnly() {
		assertEquals("127.0.0.1", shippedProperties().getProperty("server.address"));
	}

	/**
	 * A port of its own, so the three MCP servers this repository ships run side by side
	 * on one host: the uniqueness server takes 8081 and the context server 8082.
	 */
	@Test
	void servesOnItsOwnPort() {
		assertEquals("8083", shippedProperties().getProperty("server.port"));
	}

	private Properties shippedProperties() {
		Properties properties = new Properties();
		try (InputStream shipped = Main.class.getResourceAsStream(RESOURCE)) {
			assertNotNull(shipped, RESOURCE + " is missing from the server jar");
			properties.load(shipped);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + RESOURCE, e);
		}
		return properties;
	}
}

package io.github.adamw7.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Runs alone under the class-parallel unit-test run: it clears the transport,
 * web-application-type and banner system properties that
 * {@link TransportConfigurer} sets process-wide, which a concurrently running
 * server test would otherwise find missing.
 */
@Isolated
public class TransportConfigurerTest {

	private static final String TRANSPORT_MODE = "transport.mode";
	private static final String WEB_APPLICATION_TYPE = "spring.main.web-application-type";
	private static final String BANNER_MODE = "spring.main.banner-mode";

	@BeforeEach
	@AfterEach
	public void clearRuntimeProperties() {
		System.clearProperty(TRANSPORT_MODE);
		System.clearProperty(WEB_APPLICATION_TYPE);
		System.clearProperty(BANNER_MODE);
		System.clearProperty("banner-mode");
	}

	@Test
	public void defaultsToStdioWhenNoArguments() {
		assertSame(TransportMode.STDIO, TransportConfigurer.resolveTransportMode(new String[] {}));
	}

	@Test
	public void defaultsToStdioWhenPrefixAbsent() {
		assertSame(TransportMode.STDIO,
				TransportConfigurer.resolveTransportMode(new String[] { "--server.port=8080", "--other" }));
	}

	@Test
	public void readsExplicitTransportMode() {
		assertSame(TransportMode.STATELESS_HTTP,
				TransportConfigurer.resolveTransportMode(new String[] { "--transport.mode=stateless-http" }));
	}

	@Test
	public void findsTransportModeAmongOtherArguments() {
		String[] args = { "--server.port=8080", "--transport.mode=streamable-http" };
		assertSame(TransportMode.STREAMABLE_HTTP, TransportConfigurer.resolveTransportMode(args));
	}

	/**
	 * A mode nothing recognises is the argument that used to start a healthy-looking
	 * server serving no endpoint: it is not stdio, so the web server stayed on, and it
	 * matched no transport condition, so nothing was ever registered at /mcp. It is
	 * refused at the argument instead, and the message names what may be written there.
	 */
	@Test
	public void refusesAnUnknownTransportMode() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
				() -> TransportConfigurer.resolveTransportMode(new String[] { "--transport.mode=streamablehttp" }));

		assertTrue(thrown.getMessage().contains("streamablehttp"), thrown.getMessage());
		assertTrue(thrown.getMessage().contains("stdio, streamable-http, stateless-http"), thrown.getMessage());
	}

	@Test
	public void refusesAnEmptyTransportModeValue() {
		assertThrows(IllegalArgumentException.class,
				() -> TransportConfigurer.resolveTransportMode(new String[] { "--transport.mode=" }));
	}

	/**
	 * Nothing is primed for a run that cannot start, so a refused mode leaves the JVM
	 * exactly as it found it rather than half-configured for a server that never runs.
	 */
	@Test
	public void refusedModeSetsNoRuntimeProperty() {
		assertThrows(IllegalArgumentException.class,
				() -> TransportConfigurer.configure(new String[] { "--transport.mode=streamable_http" }));

		assertNull(System.getProperty(TRANSPORT_MODE));
		assertNull(System.getProperty(WEB_APPLICATION_TYPE));
		assertNull(System.getProperty(BANNER_MODE));
	}

	@Test
	public void configureDisablesBannerWithTheSpringProperty() {
		TransportConfigurer.configure(new String[] {});

		// The banner must be disabled through the property Spring actually reads;
		// the bare "banner-mode" key has no effect and would leave the banner on
		// stdout, corrupting the stdio JSON-RPC channel.
		assertEquals("off", System.getProperty(BANNER_MODE));
		assertNull(System.getProperty("banner-mode"));
	}

	@Test
	public void configurePropagatesTransportMode() {
		TransportConfigurer.configure(new String[] { "--transport.mode=streamable-http" });

		assertEquals("streamable-http", System.getProperty(TRANSPORT_MODE));
	}

	@Test
	public void configureDefaultsTransportModeToStdio() {
		TransportConfigurer.configure(new String[] {});

		assertEquals("stdio", System.getProperty(TRANSPORT_MODE));
	}

	@Test
	public void configureForcesNonWebApplicationInStdioMode() {
		TransportConfigurer.configure(new String[] {});

		assertEquals("none", System.getProperty(WEB_APPLICATION_TYPE));
	}

	@Test
	public void configureKeepsWebApplicationForStreamableHttpMode() {
		TransportConfigurer.configure(new String[] { "--transport.mode=streamable-http" });

		// A web server is required to expose the /mcp servlet, so the web
		// application type must not be forced to "none" here.
		assertNull(System.getProperty(WEB_APPLICATION_TYPE));
	}

	@Test
	public void configureKeepsWebApplicationForStatelessHttpMode() {
		TransportConfigurer.configure(new String[] { "--transport.mode=stateless-http" });

		// The stateless HTTP transport exposes the /mcp servlet, so it needs a web
		// server and must not be forced to "none".
		assertEquals("stateless-http", System.getProperty(TRANSPORT_MODE));
		assertNull(System.getProperty(WEB_APPLICATION_TYPE));
	}
}

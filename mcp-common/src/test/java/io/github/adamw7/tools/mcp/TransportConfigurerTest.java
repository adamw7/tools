package io.github.adamw7.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		assertEquals("stdio", TransportConfigurer.resolveTransportMode(new String[] {}));
	}

	@Test
	public void defaultsToStdioWhenPrefixAbsent() {
		assertEquals("stdio", TransportConfigurer.resolveTransportMode(new String[] { "--server.port=8080", "--other" }));
	}

	@Test
	public void readsExplicitTransportMode() {
		assertEquals("sse", TransportConfigurer.resolveTransportMode(new String[] { "--transport.mode=sse" }));
	}

	@Test
	public void findsTransportModeAmongOtherArguments() {
		String[] args = { "--server.port=8080", "--transport.mode=streamable-http" };
		assertEquals("streamable-http", TransportConfigurer.resolveTransportMode(args));
	}

	@Test
	public void supportsEmptyTransportModeValue() {
		assertEquals("", TransportConfigurer.resolveTransportMode(new String[] { "--transport.mode=" }));
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

	@Test
	public void configureKeepsWebApplicationForSseMode() {
		TransportConfigurer.configure(new String[] { "--transport.mode=sse" });

		// The SSE transport exposes the /sse and /mcp/message servlets, so it
		// likewise needs a web server and must not be forced to "none".
		assertEquals("sse", System.getProperty(TRANSPORT_MODE));
		assertNull(System.getProperty(WEB_APPLICATION_TYPE));
	}
}

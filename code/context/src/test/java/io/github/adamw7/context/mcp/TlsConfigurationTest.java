package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

public class TlsConfigurationTest {

	private final TlsConfiguration config = new TlsConfiguration();

	private String savedNamedGroups;

	@BeforeEach
	void clearNamedGroups() {
		savedNamedGroups = System.clearProperty(TlsConfiguration.NAMED_GROUPS_PROPERTY);
	}

	@AfterEach
	void restoreNamedGroups() {
		if (savedNamedGroups == null) {
			System.clearProperty(TlsConfiguration.NAMED_GROUPS_PROPERTY);
		} else {
			System.setProperty(TlsConfiguration.NAMED_GROUPS_PROPERTY, savedNamedGroups);
		}
	}

	@Test
	void pinsEnabledHttpsToTls13() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		factory.setSsl(enabledSsl(new String[] { "TLSv1.2", "TLSv1.3" }));

		config.enforceTls13().customize(factory);

		assertArrayEquals(new String[] { TlsConfiguration.TLS_1_3 }, factory.getSsl().getEnabledProtocols());
	}

	@Test
	void pinsHttpsEvenWhenNoProtocolWasRequested() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		factory.setSsl(enabledSsl(null));

		config.enforceTls13().customize(factory);

		assertArrayEquals(new String[] { TlsConfiguration.TLS_1_3 }, factory.getSsl().getEnabledProtocols());
	}

	@Test
	void prefersHybridKeyExchangeWhenHttpsEnabled() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		factory.setSsl(enabledSsl(new String[] { "TLSv1.3" }));

		config.enforceTls13().customize(factory);

		String namedGroups = System.getProperty(TlsConfiguration.NAMED_GROUPS_PROPERTY);
		assertEquals(TlsConfiguration.PREFERRED_NAMED_GROUPS, namedGroups);
		assertTrue(namedGroups.startsWith(TlsConfiguration.HYBRID_KEY_EXCHANGE_GROUP),
				"the hybrid group must be listed first so it is preferred");
		assertTrue(namedGroups.contains("x25519"), "a classical fallback group must remain so the handshake degrades gracefully");
	}

	@Test
	void leavesPlainHttpUntouched() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();

		config.enforceTls13().customize(factory);

		assertNull(factory.getSsl());
		assertNull(System.getProperty(TlsConfiguration.NAMED_GROUPS_PROPERTY));
	}

	@Test
	void leavesDisabledSslUntouched() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		Ssl ssl = enabledSsl(new String[] { "TLSv1.2" });
		ssl.setEnabled(false);
		factory.setSsl(ssl);

		config.enforceTls13().customize(factory);

		assertArrayEquals(new String[] { "TLSv1.2" }, factory.getSsl().getEnabledProtocols());
		assertNull(System.getProperty(TlsConfiguration.NAMED_GROUPS_PROPERTY));
	}

	private Ssl enabledSsl(String[] protocols) {
		Ssl ssl = new Ssl();
		ssl.setEnabled(true);
		ssl.setEnabledProtocols(protocols);
		return ssl;
	}
}

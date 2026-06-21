package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

public class TlsConfigurationTest {

	private final TlsConfiguration config = new TlsConfiguration();

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
	void leavesPlainHttpUntouched() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();

		config.enforceTls13().customize(factory);

		assertNull(factory.getSsl());
	}

	@Test
	void leavesDisabledSslUntouched() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		Ssl ssl = enabledSsl(new String[] { "TLSv1.2" });
		ssl.setEnabled(false);
		factory.setSsl(ssl);

		config.enforceTls13().customize(factory);

		assertArrayEquals(new String[] { "TLSv1.2" }, factory.getSsl().getEnabledProtocols());
	}

	private Ssl enabledSsl(String[] protocols) {
		Ssl ssl = new Ssl();
		ssl.setEnabled(true);
		ssl.setEnabledProtocols(protocols);
		return ssl;
	}
}

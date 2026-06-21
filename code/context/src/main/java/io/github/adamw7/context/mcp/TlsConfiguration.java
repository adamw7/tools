package io.github.adamw7.context.mcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pins the embedded server's HTTPS to TLS 1.3. When the streamable HTTP
 * transport is served over HTTPS (SSL enabled through {@code server.ssl.*}), this
 * customiser forces the only enabled protocol to {@code TLSv1.3}, regardless of
 * what the configuration requested, so older, weaker protocols can never be
 * negotiated. Plain HTTP and disabled SSL are left untouched — there is no TLS to
 * constrain in those cases.
 */
@Configuration
public class TlsConfiguration {

	static final String TLS_1_3 = "TLSv1.3";

	private static final Logger log = LogManager.getLogger(TlsConfiguration.class.getName());

	@Bean
	public WebServerFactoryCustomizer<TomcatServletWebServerFactory> enforceTls13() {
		return factory -> pinToTls13(factory.getSsl());
	}

	private void pinToTls13(Ssl ssl) {
		if (ssl != null && ssl.isEnabled()) {
			log.info("Pinning HTTPS to {}", TLS_1_3);
			ssl.setEnabledProtocols(new String[] { TLS_1_3 });
		}
	}
}

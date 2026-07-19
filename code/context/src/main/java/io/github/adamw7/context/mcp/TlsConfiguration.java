package io.github.adamw7.context.mcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hardens the embedded server's HTTPS transport. When the streamable HTTP
 * transport is served over HTTPS (SSL enabled through {@code server.ssl.*}), this
 * customiser applies two guarantees regardless of what the deployment
 * configuration requested, so they hold independent of how {@code server.ssl.*}
 * is set:
 *
 * <ol>
 * <li>the only enabled protocol is pinned to {@code TLSv1.3}, so older, weaker
 * protocols can never be negotiated; and</li>
 * <li>the post-quantum {@code X25519MLKEM768} hybrid group is preferred for the
 * TLS 1.3 key exchange, with classical elliptic-curve groups kept as a fallback
 * so peers that cannot do the hybrid exchange still connect.</li>
 * </ol>
 *
 * <p>{@code X25519MLKEM768} combines a NIST ML-KEM-768 key encapsulation with the
 * classical X25519 exchange; the derived secret stays secure as long as
 * <em>either</em> primitive holds, which defends against "harvest now, decrypt
 * later" attacks by a future quantum adversary. It is requested through the
 * JVM-wide {@code jdk.tls.namedGroups} property, which lists the hybrid group
 * first (so it is preferred) followed by classical groups (so the handshake
 * degrades gracefully). Because that list also names supported classical groups,
 * a JDK whose TLS provider does not yet ship {@code X25519MLKEM768} — such as the
 * SunJSSE provider in JDK 25 — simply ignores the unknown group and negotiates a
 * classical one; the hybrid exchange then activates automatically once the
 * provider supports it, with no code change. The property is set only when HTTPS
 * is enabled, before the connector starts its TLS stack, so SunJSSE reads it when
 * initialising its supported groups.
 *
 * <p>Plain HTTP and disabled SSL are left untouched — there is no TLS to
 * constrain in those cases.
 */
@Configuration
public class TlsConfiguration {

	static final String TLS_1_3 = "TLSv1.3";

	static final String HYBRID_KEY_EXCHANGE_GROUP = "X25519MLKEM768";

	static final String NAMED_GROUPS_PROPERTY = "jdk.tls.namedGroups";

	static final String PREFERRED_NAMED_GROUPS = HYBRID_KEY_EXCHANGE_GROUP + ",x25519,secp256r1,secp384r1";

	private static final Logger log = LogManager.getLogger(TlsConfiguration.class.getName());

	@Bean
	public WebServerFactoryCustomizer<TomcatServletWebServerFactory> enforceTls13() {
		return factory -> hardenHttps(factory.getSsl());
	}

	private void hardenHttps(Ssl ssl) {
		if (ssl != null && ssl.isEnabled()) {
			pinToTls13(ssl);
			preferHybridKeyExchange();
		}
	}

	private void pinToTls13(Ssl ssl) {
		log.info("Pinning HTTPS to {}", TLS_1_3);
		ssl.setEnabledProtocols(new String[] { TLS_1_3 });
	}

	private void preferHybridKeyExchange() {
		log.info("Preferring {} hybrid key exchange for TLS 1.3", HYBRID_KEY_EXCHANGE_GROUP);
		System.setProperty(NAMED_GROUPS_PROPERTY, PREFERRED_NAMED_GROUPS);
	}
}

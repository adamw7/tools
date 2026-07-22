package io.github.adamw7.tools.data.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises the {@link ProxySelector} that {@link Switch#off()} installs as the
 * JVM default. The switch is process-global and already engaged for the unit-test
 * run, but {@link #engageKillSwitch()} calls {@link Switch#off()} defensively so
 * the class also holds when run in isolation — the call is an idempotent no-op
 * once the network is off. Every outbound-connection attempt routes through
 * {@code select}, so proving it rejects every scheme proves the network is sealed;
 * {@code connectFailed} is the callback the JDK invokes when a chosen proxy fails
 * and must stay a quiet no-op so it never masks the block.
 */
public class BlockingProxySelectorTest {

	@BeforeAll
	static void engageKillSwitch() {
		Switch.off();
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://192.0.2.1", "https://192.0.2.1:8443/path",
			"ftp://192.0.2.1/file", "ws://192.0.2.1/socket", "file:///etc/hosts" })
	public void selectRejectsEveryScheme(String uri) {
		UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
				() -> ProxySelector.getDefault().select(URI.create(uri)),
				"the blocking selector must refuse proxy selection for " + uri);

		assertEquals("The network is off", thrown.getMessage());
	}

	@Test
	public void connectFailedIsASilentNoOp() {
		// createUnresolved avoids any DNS lookup, so the assertion stays offline.
		InetSocketAddress unreachable = InetSocketAddress.createUnresolved("192.0.2.1", 80);

		assertDoesNotThrow(() -> ProxySelector.getDefault().connectFailed(URI.create("http://192.0.2.1"),
				unreachable, new IOException("simulated connect failure")),
				"connectFailed is a callback, not a gate; it must never throw");
	}

	@Test
	public void defaultSelectorStaysTheBlockingOneAcrossCalls() {
		ProxySelector first = ProxySelector.getDefault();
		ProxySelector second = ProxySelector.getDefault();

		assertSame(first, second, "the installed blocking selector must remain the JVM default");
		assertThrows(UnsupportedOperationException.class,
				() -> first.select(URI.create("https://192.0.2.1")),
				"the retained selector must still block");
	}
}

package io.github.adamw7.tools.data.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.ProxySelector;
import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Proves that the {@link NetworkOffExtension} really engaged the kill-switch for
 * the unit-test run: by the time any unit test executes, the network is already
 * off, so this test does not call {@link Switch#off()} itself. The test is gated
 * on the {@code tools.test.network.off} property so it only asserts the guarantee
 * where it is expected to hold — the surefire build — and is simply skipped when a
 * developer runs a single test from an IDE without that property.
 */
@EnabledIfSystemProperty(named = "tools.test.network.off", matches = "true")
public class NetworkOffDuringUnitTestsTest {

	@Test
	public void networkIsAlreadyOffBeforeTheTestRuns() {
		UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
				() -> ProxySelector.getDefault().select(URI.create("http://192.0.2.1")),
				"The kill-switch should have blocked proxy selection before the test ran");

		assertEquals("The network is off", thrown.getMessage());
	}
}

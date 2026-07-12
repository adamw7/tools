package io.github.adamw7.tools.data.network;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Engages the {@link Switch} network kill-switch before any unit test runs, so a
 * unit test can never open an outbound connection. The extension is discovered
 * automatically (JUnit Jupiter extension auto-detection, registered through
 * {@code META-INF/services}) and enabled only for the surefire unit-test run:
 * failsafe leaves auto-detection off, so the MCP {@code *IT} integration tests
 * that need real network are unaffected. The {@code tools.test.network.off}
 * system property — set by surefire, absent under failsafe — is a second guard
 * so the kill-switch stays off unless the unit-test run explicitly asked for it.
 */
public class NetworkOffExtension implements BeforeAllCallback {

	private static final String NETWORK_OFF_PROPERTY = "tools.test.network.off";

	private static volatile boolean engaged = false;

	/**
	 * Runs before every test class, but engages the kill-switch only once per fork:
	 * {@link Switch#off()} is idempotent and would otherwise log an "already off"
	 * warning for every class after the first.
	 */
	@Override
	public void beforeAll(ExtensionContext context) {
		if (!engaged && Boolean.getBoolean(NETWORK_OFF_PROPERTY)) {
			Switch.off();
			engaged = true;
		}
	}
}

package io.github.adamw7.tools.data.network;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Engages the {@link Switch} network kill-switch before a test runs, so the test
 * can never open an outbound connection. There are two ways it activates:
 *
 * <ul>
 * <li><b>Module-wide.</b> The extension is discovered automatically (JUnit Jupiter
 * extension auto-detection, registered through {@code META-INF/services}) and
 * enabled only for the surefire unit-test run: failsafe leaves auto-detection off,
 * so the MCP {@code *IT} integration tests that need real network are unaffected.
 * The {@code tools.test.network.off} system property — set by surefire, absent
 * under failsafe — is a second guard so the kill-switch stays off unless the
 * unit-test run explicitly asked for it.</li>
 * <li><b>Per test.</b> Annotating a test class with {@link NetworkOff} registers
 * this extension explicitly. An explicit request engages the kill-switch
 * unconditionally — the property guard is bypassed — so the network is off even
 * when a developer runs that single test from an IDE.</li>
 * </ul>
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
		if (!engaged && (explicitlyRequested(context) || Boolean.getBoolean(NETWORK_OFF_PROPERTY))) {
			Switch.off();
			engaged = true;
		}
	}

	/**
	 * True when the test class opted in with {@link NetworkOff}, as opposed to the
	 * module-wide auto-detected registration. An explicit opt-in engages the
	 * kill-switch regardless of the {@code tools.test.network.off} property.
	 */
	private boolean explicitlyRequested(ExtensionContext context) {
		return AnnotationSupport.isAnnotated(context.getElement(), NetworkOff.class);
	}
}

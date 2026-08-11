package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.junit.jupiter.api.Test;

class SilentEnforcerLoggerTest {

	private final EnforcerLogger log = SilentEnforcerLogger.INSTANCE;

	/**
	 * A rule asks before building an expensive message, so every level has to answer
	 * that there is no point: a logger that dropped the message but claimed to be
	 * enabled would still make the rule pay for it.
	 */
	@Test
	void reportsEveryLevelDisabled() {
		assertFalse(log.isDebugEnabled());
		assertFalse(log.isInfoEnabled());
		assertFalse(log.isWarnEnabled());
		assertFalse(log.isErrorEnabled());
	}

	@Test
	void dropsEveryMessageItIsGiven() {
		assertDoesNotThrow(() -> {
			log.debug("debug");
			log.info("info");
			log.warn("warn");
			log.warnOrError("warnOrError");
			log.error("error");
		});
	}

	/**
	 * The suppliers are never invoked, so a rule that logs the result of work it
	 * would rather not do does not do it.
	 */
	@Test
	void neverInvokesASuppliedMessage() {
		assertDoesNotThrow(() -> {
			log.debug(SilentEnforcerLoggerTest::unwanted);
			log.info(SilentEnforcerLoggerTest::unwanted);
			log.warn(SilentEnforcerLoggerTest::unwanted);
			log.warnOrError(SilentEnforcerLoggerTest::unwanted);
			log.error(SilentEnforcerLoggerTest::unwanted);
		});
	}

	private static CharSequence unwanted() {
		throw new IllegalStateException("a dropped message must not be built");
	}
}

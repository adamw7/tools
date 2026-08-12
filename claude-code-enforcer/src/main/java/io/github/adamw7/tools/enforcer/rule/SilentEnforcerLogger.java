package io.github.adamw7.tools.enforcer.rule;

import java.util.function.Supplier;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;

/**
 * The logger a rule uses when it was never given one: every level disabled and
 * every message dropped.
 * <p>
 * maven-enforcer injects a logger before it runs a rule, but nothing else does.
 * A rule constructed directly — by a unit test, or by any caller embedding these
 * checks outside a Maven session — has a null logger, so a rule that logged what
 * it checked would throw on the logging rather than report on what it checked.
 * That is why the two debug lines this module already had were confined to paths
 * a test is unlikely to reach. Falling back to this instance removes the
 * constraint: a rule may log wherever logging is useful, and the caller that
 * wants to see it supplies a logger.
 *
 * @see ClaudeCodeEnforcerRule#log()
 */
final class SilentEnforcerLogger implements EnforcerLogger {

	static final EnforcerLogger INSTANCE = new SilentEnforcerLogger();

	private SilentEnforcerLogger() {
	}

	@Override
	public boolean isDebugEnabled() {
		return false;
	}

	@Override
	public void debug(CharSequence message) {
	}

	@Override
	public void debug(Supplier<CharSequence> message) {
	}

	@Override
	public boolean isInfoEnabled() {
		return false;
	}

	@Override
	public void info(CharSequence message) {
	}

	@Override
	public void info(Supplier<CharSequence> message) {
	}

	@Override
	public boolean isWarnEnabled() {
		return false;
	}

	@Override
	public void warn(CharSequence message) {
	}

	@Override
	public void warn(Supplier<CharSequence> message) {
	}

	@Override
	public void warnOrError(CharSequence message) {
	}

	@Override
	public void warnOrError(Supplier<CharSequence> message) {
	}

	@Override
	public boolean isErrorEnabled() {
		return false;
	}

	@Override
	public void error(CharSequence message) {
	}

	@Override
	public void error(Supplier<CharSequence> message) {
	}
}

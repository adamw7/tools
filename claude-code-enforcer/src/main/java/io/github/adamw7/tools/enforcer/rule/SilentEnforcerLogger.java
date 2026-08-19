package io.github.adamw7.tools.enforcer.rule;

import java.util.function.Supplier;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;

/**
 * The logger a rule uses when it was never given one: every level disabled and
 * every message dropped.
 * <p>
 * maven-enforcer injects a logger before it runs a rule, but nothing else does, so
 * a rule constructed directly — by a unit test, or by a caller embedding these
 * checks outside a Maven session — would throw on the logging rather than report on
 * what it checked. Falling back to this instance lets a rule log wherever logging
 * is useful, with the caller that wants to see it supplying a logger.
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

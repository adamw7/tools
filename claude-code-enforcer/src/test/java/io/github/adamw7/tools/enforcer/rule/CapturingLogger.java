package io.github.adamw7.tools.enforcer.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;

/**
 * A test double for {@link EnforcerLogger} that records the warnings a rule
 * emits, so warn-severity behaviour can be asserted without a Maven runtime.
 */
public class CapturingLogger implements EnforcerLogger {

	private final List<String> warnings = new ArrayList<>();

	public List<String> warnings() {
		return warnings;
	}

	@Override
	public void warn(CharSequence message) {
		warnings.add(String.valueOf(message));
	}

	@Override
	public void warn(Supplier<CharSequence> message) {
		warnings.add(String.valueOf(message.get()));
	}

	@Override
	public void warnOrError(CharSequence message) {
		warnings.add(String.valueOf(message));
	}

	@Override
	public void warnOrError(Supplier<CharSequence> message) {
		warnings.add(String.valueOf(message.get()));
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
		return true;
	}

	@Override
	public boolean isErrorEnabled() {
		return true;
	}

	@Override
	public void error(CharSequence message) {
	}

	@Override
	public void error(Supplier<CharSequence> message) {
	}
}

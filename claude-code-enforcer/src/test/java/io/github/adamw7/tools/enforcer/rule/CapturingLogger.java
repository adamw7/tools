package io.github.adamw7.tools.enforcer.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;

/**
 * A test double for {@link EnforcerLogger} that records what a rule emits, so
 * warn-severity behaviour and the debug trace of what a rule checked can both be
 * asserted without a Maven runtime.
 * <p>
 * Every level reports itself as enabled, because a rule that guards a message
 * behind {@code isDebugEnabled()} would otherwise never produce the message the
 * assertion is looking for.
 */
public class CapturingLogger implements EnforcerLogger {

	private final List<String> warnings = new ArrayList<>();
	private final List<String> infos = new ArrayList<>();
	private final List<String> debugs = new ArrayList<>();

	public List<String> warnings() {
		return warnings;
	}

	public List<String> infos() {
		return infos;
	}

	public List<String> debugs() {
		return debugs;
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
		return true;
	}

	@Override
	public void debug(CharSequence message) {
		debugs.add(String.valueOf(message));
	}

	@Override
	public void debug(Supplier<CharSequence> message) {
		debugs.add(String.valueOf(message.get()));
	}

	@Override
	public boolean isInfoEnabled() {
		return true;
	}

	@Override
	public void info(CharSequence message) {
		infos.add(String.valueOf(message));
	}

	@Override
	public void info(Supplier<CharSequence> message) {
		infos.add(String.valueOf(message.get()));
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

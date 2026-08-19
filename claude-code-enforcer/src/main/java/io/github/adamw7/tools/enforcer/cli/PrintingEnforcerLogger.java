package io.github.adamw7.tools.enforcer.cli;

import java.io.PrintStream;
import java.util.function.Supplier;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;

/**
 * The logger the command line runs a check with: maven-enforcer's own logger
 * interface, writing to a stream instead of to a Maven session.
 *
 * <p>The rules already log everything they have to say through that interface —
 * what each was pointed at, what it suppressed, what it repaired — and none of
 * them knows or cares who is listening. So running them outside Maven needs no
 * second reporting path, only somewhere for this one to go.
 *
 * <p>Debug is off unless asked for, because a rule's debug line says which inputs
 * a verdict was reached on and that is a question an operator asks after a
 * surprising answer, not before one.
 */
final class PrintingEnforcerLogger implements EnforcerLogger {

	private final PrintStream out;
	private final PrintStream err;
	private final boolean debugEnabled;

	PrintingEnforcerLogger(PrintStream out, PrintStream err, boolean debugEnabled) {
		this.out = out;
		this.err = err;
		this.debugEnabled = debugEnabled;
	}

	@Override
	public boolean isDebugEnabled() {
		return debugEnabled;
	}

	@Override
	public void debug(CharSequence message) {
		if (debugEnabled) {
			out.println("[debug] " + message);
		}
	}

	@Override
	public void debug(Supplier<CharSequence> message) {
		if (debugEnabled) {
			debug(message.get());
		}
	}

	@Override
	public boolean isInfoEnabled() {
		return true;
	}

	@Override
	public void info(CharSequence message) {
		out.println("[info] " + message);
	}

	@Override
	public void info(Supplier<CharSequence> message) {
		info(message.get());
	}

	@Override
	public boolean isWarnEnabled() {
		return true;
	}

	@Override
	public void warn(CharSequence message) {
		err.println("[warn] " + message);
	}

	@Override
	public void warn(Supplier<CharSequence> message) {
		warn(message.get());
	}

	/**
	 * maven-enforcer calls this where a message's level depends on whether the rule
	 * that produced it fails the build. Off the command line the answer is always the
	 * same — the run reports what it found and the caller decides — so it reads as a
	 * warning, which is the level that does not claim a verdict.
	 */
	@Override
	public void warnOrError(CharSequence message) {
		warn(message);
	}

	@Override
	public void warnOrError(Supplier<CharSequence> message) {
		warn(message);
	}

	@Override
	public boolean isErrorEnabled() {
		return true;
	}

	@Override
	public void error(CharSequence message) {
		err.println("[error] " + message);
	}

	@Override
	public void error(Supplier<CharSequence> message) {
		error(message.get());
	}
}

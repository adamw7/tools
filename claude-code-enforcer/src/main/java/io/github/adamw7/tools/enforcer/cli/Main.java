package io.github.adamw7.tools.enforcer.cli;

import java.io.File;
import java.io.PrintStream;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.project.ClaudeCodeProjectRule;

/**
 * Command-line entry point: checks a project's Claude Code configuration without
 * a Maven build around it.
 *
 * <p>A maven-enforcer rule has to be resolvable as a JAR before the build using it
 * runs, which is the right cost for a check that gates a build and the wrong one
 * for a pre-commit hook, a project built with something else, or anyone wanting to
 * know what the rules make of a repository before wiring anything. The rules never
 * needed Maven — they read files and collect violations — so this runs them
 * directly:
 *
 * <pre>{@code
 * java -jar claude-code-enforcer.jar /path/to/project
 * java -jar claude-code-enforcer.jar . --fix --skip okfBundleFormat
 * }</pre>
 *
 * <p>Everything it can be asked is a parameter of {@link ClaudeCodeProjectRule},
 * so the command line and a pom configure one thing rather than two that could
 * come to disagree.
 *
 * <p>Failure is reported by throwing, as everywhere else here, which exits the
 * process non-zero without this class ending a JVM it does not own. The violations
 * are printed first, so what a hook shows its user is the report.
 */
public final class Main {

	private Main() {
	}

	public static void main(String[] args) {
		run(args, System.out, System.err);
	}

	/**
	 * The run with its streams supplied, so a test reads what an operator would see.
	 *
	 * @throws EnforcerRuleException when the configuration is not valid, or the
	 *                               command line could not be read
	 */
	static void run(String[] args, PrintStream out, PrintStream err) {
		CheckArguments arguments = CheckArguments.parse(args);
		if (arguments.helpRequested()) {
			out.println(CheckArguments.USAGE);
			return;
		}
		check(arguments, new PrintingEnforcerLogger(out, err, arguments.debug()), out);
	}

	private static void check(CheckArguments arguments, EnforcerLogger logger, PrintStream out) {
		reportsGoTo(arguments.reportDirectory());
		ClaudeCodeProjectRule rule = arguments.rule();
		rule.setLog(logger);
		try {
			rule.execute();
			out.println("Claude Code configuration is valid.");
		} catch (EnforcerRuleException e) {
			logger.error(e.getMessage());
			throw new CheckFailedException();
		}
	}

	/**
	 * Points the rules at a report directory through the property they already read,
	 * rather than through a parameter of its own. There is one way a build asks for
	 * reports and this is it; a second would be a second thing to keep in step.
	 */
	private static void reportsGoTo(File reportDirectory) {
		if (reportDirectory != null) {
			System.setProperty("claude.enforcer.reportDir", reportDirectory.getPath());
		}
	}

	/**
	 * What a failed check throws, so the process exits non-zero without this class
	 * ending a JVM it does not own. It deliberately carries neither the rule's
	 * message nor its exception as a cause: either would put the whole report into
	 * the stack trace a shell prints after it, and the report has already been
	 * printed. This only says that it was one.
	 */
	static final class CheckFailedException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		CheckFailedException() {
			super("The Claude Code configuration is not valid; see the violations above");
		}
	}
}

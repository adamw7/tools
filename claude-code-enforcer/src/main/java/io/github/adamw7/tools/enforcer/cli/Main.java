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
 * <p>A maven-enforcer rule has to be resolvable as a JAR before the build that
 * uses it runs, which is why checking this repository's own documents takes two
 * commands and why its CI needs a bootstrap step. That is the right cost for a
 * check that gates a build; it is the wrong one for a pre-commit hook, for a
 * project built with Gradle or with nothing, and for anyone who wants to know
 * what the rules make of a repository before wiring anything at all. The rules
 * never needed Maven — they read files and collect violations — so this runs them
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
 * <p>Failure is reported by throwing, as everywhere else in this repository, which
 * leaves the process exiting non-zero without this class deciding to end a JVM it
 * does not own. The violations are printed first, so what a hook shows its user is
 * the report rather than the throw.
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
	 * ending a JVM it does not own.
	 *
	 * <p>It deliberately carries neither the rule's message nor the rule's exception
	 * as its cause. Both would put the whole report into the stack trace a shell
	 * prints after it, so an operator whose CLAUDE.md is missing six sections would
	 * read those six twice — once as the report and once as a trace. The report has
	 * already been printed; this only says that it was one.
	 */
	static final class CheckFailedException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		CheckFailedException() {
			super("The Claude Code configuration is not valid; see the violations above");
		}
	}
}

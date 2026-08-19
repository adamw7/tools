package io.github.adamw7.tools.enforcer.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.adamw7.tools.enforcer.project.ClaudeCodeProjectRule;

/**
 * The command line {@link Main} is driven by, parsed into the composite rule it
 * describes.
 *
 * <p>Every option here corresponds to a parameter of {@code claudeCodeProject},
 * because the command line exists to reach that rule from somewhere that is not a
 * Maven build — a pre-commit hook, a project built with something else — and an
 * option it did not have would be a second contract to keep in step.
 *
 * <p>An unrecognised option is refused rather than ignored. A hook invoking
 * {@code --fix-all} and being quietly given a plain check is the failure this
 * whole class exists to make impossible.
 */
final class CheckArguments {

	static final String USAGE = """
			Usage: claude-code-enforcer [<project-directory>] [options]

			Checks a project's Claude Code configuration (CLAUDE.md, AGENTS.md, and the
			.claude directory), reporting everything wrong with it at once. The project
			directory defaults to the working directory.

			Options:
			  --skip <rule>[,<rule>...]  do not run these rules, by the name each is
			                             reported under, e.g. okfBundleFormat
			  --fix                      repair what can be repaired rather than only
			                             reporting it: a missing title, a near-miss
			                             heading, an empty or absent section
			  --warn                     report violations without failing
			  --budget <bytes>           the size CLAUDE.md is held to; 0 to not check
			  --report <directory>       write an HTML report per rule, and an index
			  --debug                    say what each rule was pointed at
			  --help                     print this and check nothing""";

	private final File projectDirectory;
	private final List<String> skippedRules;
	private final boolean fix;
	private final boolean warn;
	private final boolean debug;
	private final boolean helpRequested;
	private final Integer budget;
	private final File reportDirectory;

	private CheckArguments(Parsed parsed) {
		this.projectDirectory = parsed.projectDirectory;
		this.skippedRules = List.copyOf(parsed.skippedRules);
		this.fix = parsed.fix;
		this.warn = parsed.warn;
		this.debug = parsed.debug;
		this.helpRequested = parsed.helpRequested;
		this.budget = parsed.budget;
		this.reportDirectory = parsed.reportDirectory;
	}

	/**
	 * @throws IllegalArgumentException when an option is unrecognised, missing its
	 *                                  value, or a second project directory is named
	 */
	static CheckArguments parse(String... args) {
		Parsed parsed = new Parsed();
		for (int index = 0; index < args.length; index++) {
			index = parsed.read(args, index);
		}
		return new CheckArguments(parsed);
	}

	boolean helpRequested() {
		return helpRequested;
	}

	boolean debug() {
		return debug;
	}

	/** @return where the reports go, or null when none were asked for */
	File reportDirectory() {
		return reportDirectory;
	}

	/** The rule this command line describes, configured and ready to run. */
	ClaudeCodeProjectRule rule() {
		ClaudeCodeProjectRule rule = new ClaudeCodeProjectRule();
		rule.setProjectDir(projectDirectory == null ? new File(".") : projectDirectory);
		rule.setSkippedRules(skippedRules);
		rule.setAutoFix(fix);
		if (warn) {
			rule.setSeverity("warn");
		}
		if (budget != null) {
			rule.setClaudeMdBudgetBytes(budget);
		}
		return rule;
	}

	/** The options as they accumulate, so the parse reads as one pass over the arguments. */
	private static final class Parsed {

		private File projectDirectory;
		private final List<String> skippedRules = new ArrayList<>();
		private boolean fix;
		private boolean warn;
		private boolean debug;
		private boolean helpRequested;
		private Integer budget;
		private File reportDirectory;

		/** @return the index of the last argument consumed, so the caller advances past a value */
		private int read(String[] args, int index) {
			String argument = args[index];
			return switch (argument) {
				case "--fix" -> flag(() -> fix = true, index);
				case "--warn" -> flag(() -> warn = true, index);
				case "--debug" -> flag(() -> debug = true, index);
				case "--help", "-h" -> flag(() -> helpRequested = true, index);
				case "--skip" -> readSkipped(value(args, index));
				case "--budget" -> readBudget(value(args, index));
				case "--report" -> readReport(value(args, index));
				default -> readPositional(argument, index);
			};
		}

		private int flag(Runnable set, int index) {
			set.run();
			return index;
		}

		private int readSkipped(Value value) {
			for (String name : value.text.split(",")) {
				addSkipped(name);
			}
			return value.index;
		}

		private void addSkipped(String name) {
			if (!name.isBlank()) {
				skippedRules.add(name.strip());
			}
		}

		private int readBudget(Value value) {
			try {
				budget = Integer.valueOf(value.text.strip());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("--budget takes a number of bytes, not '" + value.text + "'", e);
			}
			return value.index;
		}

		private int readReport(Value value) {
			reportDirectory = new File(value.text);
			return value.index;
		}

		/**
		 * An argument that is not an option is the project directory. One that looks
		 * like an option is refused, since a mistyped flag silently read as a directory
		 * would run the check somewhere nobody meant.
		 */
		private int readPositional(String argument, int index) {
			if (argument.startsWith("-")) {
				throw new IllegalArgumentException("Unknown option: " + argument + System.lineSeparator() + USAGE);
			}
			if (projectDirectory != null) {
				throw new IllegalArgumentException("Only one project directory can be checked, but both "
						+ projectDirectory + " and " + argument + " were named");
			}
			projectDirectory = new File(argument);
			return index;
		}

		private Value value(String[] args, int index) {
			if (index + 1 >= args.length) {
				throw new IllegalArgumentException(args[index] + " needs a value" + System.lineSeparator() + USAGE);
			}
			return new Value(args[index + 1], index + 1);
		}
	}

	/** An option's value together with the index it was read from. */
	private record Value(String text, int index) {
	}
}

package io.github.adamw7.tools.enforcer.e2e;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What maven-enforcer said about each rule, read back out of a build log.
 * <p>
 * A test that only asks whether the build passed cannot tell a rule that examined a
 * project and found nothing wrong from one that never ran at all, and the second is
 * the failure worth catching: the whole reason the {@code *IT}s exist is that a rule
 * can be correct and still be skipped. maven-enforcer reports every rule
 * individually and evaluates them all even after one has failed —
 * <pre>
 * [INFO] Rule 3: io.github...McpConfigFormatRule(mcpConfigFormat) passed
 * [ERROR] Rule 0: io.github...ClaudeMdFormatRule(claudeMdFormat) failed with message:
 * [ERROR] CLAUDE.md does not exist at /checkout/CLAUDE.md
 * </pre>
 * — so the log carries a verdict per rule, and reading it back turns "the build
 * failed" into "these rules failed, for these reasons, and every other one passed".
 * <p>
 * Verdicts are keyed by the name a pom addresses the rule with, which is the
 * {@code @Named} name of the class behind it, so a caller names rules the way the
 * configuration does rather than by class.
 */
final class EnforcerVerdicts {

	/**
	 * The rule line, whichever log level carried it. The class name butts against the
	 * bracketed name with no space, and the group taken is that name.
	 */
	private static final Pattern VERDICT = Pattern.compile("Rule \\d+: \\S+\\((\\w+)\\) (passed|failed)");

	/** The {@code [INFO] }/{@code [ERROR] } prefix Maven writes every line with. */
	private static final Pattern LOG_LEVEL = Pattern.compile("^\\[[A-Z]+\\]\\s?");

	/** Maven's link to its own help, which closes the block of failure messages. */
	private static final String HELP_LINK = "-> [Help";

	private static final String PASSED = "passed";

	private final Map<String, String> failures = new LinkedHashMap<>();
	private final Set<String> passes = new LinkedHashSet<>();

	private String failing;
	private final List<String> message = new ArrayList<>();

	private EnforcerVerdicts() {
	}

	/** Every verdict {@code outcome}'s log carries, in the order maven-enforcer reported them. */
	static EnforcerVerdicts in(BuildOutcome outcome) {
		EnforcerVerdicts verdicts = new EnforcerVerdicts();
		outcome.output().lines().forEach(verdicts::read);
		verdicts.finishFailure();
		return verdicts;
	}

	/** Whether the rule reported anything at all, which a rule that never ran does not. */
	boolean reached(String rule) {
		return passes.contains(rule) || failures.containsKey(rule);
	}

	/** Whether the rule passed. A rule that reached no verdict passed nothing. */
	boolean passed(String rule) {
		return passes.contains(rule);
	}

	/** Why the rule failed, or an empty string when it passed or never reported. */
	String messageOf(String rule) {
		return failures.getOrDefault(rule, "");
	}

	/** The rules that failed, for an assertion message that has to name them. */
	Set<String> failed() {
		return failures.keySet();
	}

	private void read(String line) {
		String text = LOG_LEVEL.matcher(line).replaceFirst("");
		Matcher verdict = VERDICT.matcher(text);
		if (verdict.find()) {
			start(verdict.group(1), PASSED.equals(verdict.group(2)));
		} else if (text.startsWith(HELP_LINK)) {
			finishFailure();
		} else {
			collect(text);
		}
	}

	/**
	 * A pass is recorded outright, since maven-enforcer says nothing more about it; a
	 * failure is held open so the lines under it can be gathered as its message.
	 */
	private void start(String rule, boolean passed) {
		finishFailure();
		if (passed) {
			passes.add(rule);
		} else {
			failing = rule;
		}
	}

	/** Only the lines under an open failure are its message; everything else is build noise. */
	private void collect(String text) {
		if (failing != null && !text.isBlank()) {
			message.add(text.strip());
		}
	}

	private void finishFailure() {
		if (failing != null) {
			failures.put(failing, String.join(System.lineSeparator(), message));
			failing = null;
			message.clear();
		}
	}
}

package io.github.adamw7.tools.enforcer.rule;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * The membership checks every configuration rule repeats: each value a policy list
 * names that the document offends against — a required entry it does not declare,
 * a forbidden entry it does — and each entry it declares that a whitelist does not
 * allow.
 * <p>
 * Both take the message as a function of the offending value, so a rule keeps its
 * own wording while the traversal lives here, along with the reading the rule
 * configuration gives an unset parameter: a {@code null} policy list checks
 * nothing, and a {@code null} whitelist allows anything.
 */
public final class Violations {

	private Violations() {
	}

	/** Adds {@code message} for each of {@code configured} that {@code offends} accepts. */
	public static void each(List<String> configured, Predicate<String> offends, UnaryOperator<String> message,
			List<String> violations) {
		if (configured == null) {
			return;
		}
		configured.stream().filter(offends).map(message).forEach(violations::add);
	}

	/** Adds {@code message} for each of {@code declared} outside {@code allowed}. */
	public static void eachDisallowed(Collection<String> declared, Collection<String> allowed,
			UnaryOperator<String> message, List<String> violations) {
		if (allowed == null) {
			return;
		}
		declared.stream().filter(value -> !allowed.contains(value)).map(message).forEach(violations::add);
	}
}

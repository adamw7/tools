package io.github.adamw7.tools.adopt.step;

import java.util.Locale;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * How much of the adopted repository's Claude Code configuration the guard checks.
 *
 * <p>The adoption used to wire one rule, {@code claudeMdFormat}, and the gap that
 * left was not academic: the run installs a companion {@code AGENTS.md} and, with
 * {@code --assets}, a {@code .claude} directory of settings and hooks — none of
 * which anything then checked. A repository could carry a malformed
 * {@code settings.json}, a skill with no definition, or a credential committed
 * into a hook, and its build stayed green while advertising that it had adopted
 * Claude Code.
 */
public enum GuardRules {

	/**
	 * Only the {@code CLAUDE.md} format rule. The narrowest guard that is still a
	 * guard, for a repository whose maintainers want the document checked and nothing
	 * else of theirs touched.
	 */
	MINIMAL("claudeMdFormat"),

	/**
	 * Every rule that has something to check, through the {@code claudeCodeProject}
	 * composite: the documents, the definitions, the settings and hooks, and the
	 * secret scan. It is the default because it is the one that covers what the
	 * adoption itself installs, and because it costs the adopted POM three elements
	 * rather than sixty.
	 */
	PROJECT("claudeCodeProject");

	private final String ruleName;

	GuardRules(String ruleName) {
		this.ruleName = ruleName;
	}

	/** The {@code @Named} element the adopted POM configures. */
	public String ruleName() {
		return ruleName;
	}

	/**
	 * @param name the value as an operator wrote it, in any case
	 * @throws IllegalArgumentException naming what it accepts, since a misspelt rule
	 *                                  set read as the default would quietly widen or
	 *                                  narrow what somebody else's build enforces
	 */
	public static GuardRules of(String name) {
		String normalized = name.strip().toUpperCase(Locale.ROOT);
		return Arrays.stream(values())
				.filter(candidate -> candidate.name().equals(normalized))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown rule set '" + name.strip()
						+ "'. Use one of " + names()));
	}

	private static String names() {
		return Arrays.stream(values())
				.map(value -> value.name().toLowerCase(Locale.ROOT))
				.collect(Collectors.joining(", "));
	}
}

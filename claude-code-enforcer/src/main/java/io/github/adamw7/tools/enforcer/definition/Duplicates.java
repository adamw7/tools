package io.github.adamw7.tools.enforcer.definition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the sources that claim the same value, so the rules checking that a
 * property is unique across every Claude Code definition — currently names and
 * descriptions — share one way of finding and reporting a clash.
 * <p>
 * Values are grouped by a comparison key, which lets a rule ignore differences
 * that do not matter (a description compares case- and whitespace-insensitively)
 * while still reporting the value as its author wrote it.
 */
final class Duplicates {

	/** A value as its author wrote it, plus the sources that claim an equivalent of it. */
	private record Claim(String value, List<String> sources) {
	}

	private final Map<String, Claim> byKey = new LinkedHashMap<>();

	/** Records that {@code source} claims {@code value}, grouped under {@code key}. */
	void add(String key, String value, String source) {
		byKey.computeIfAbsent(key, absent -> new Claim(value, new ArrayList<>())).sources().add(source);
	}

	/**
	 * One violation per value claimed more than once, naming the {@code property}
	 * (e.g. {@code name}) and every source that claims it.
	 */
	List<String> violations(String property) {
		return byKey.values().stream()
				.filter(claim -> claim.sources().size() > 1)
				.map(claim -> property + " '" + claim.value() + "' is used by " + claim.sources().size()
						+ " definitions: " + String.join(", ", claim.sources()))
				.toList();
	}
}

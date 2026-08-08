package io.github.adamw7.tools.adopt.step;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Compares the leading numbers of a Maven version literal against a minimum,
 * which is all the adoption asks of one: whether the plugin a project pinned is
 * too old to run the rule being wired into it.
 *
 * <p>Only the numeric prefix is read, so {@code 3.0.0-M3} compares as
 * {@code 3.0.0}. That is the safe direction for the one question asked: a
 * qualifier only ever precedes the release it qualifies, so a milestone of the
 * minimum reads as the release and is not refused, while every version genuinely
 * below it still is.
 *
 * <p>A literal carrying no leading number at all — an unsubstituted
 * {@code ${enforcer.version}}, or the empty text of a version the POM leaves to a
 * {@code pluginManagement} entry or a parent — is <em>not</em> below anything.
 * Refusing what cannot be read here would turn an ordinary POM into an unadoptable
 * one, and {@link VerifyStep} still runs the guard before any of it is pushed.
 */
final class PluginVersion {

	/** The separators Maven writes between a version's components. */
	private static final String SEPARATORS = "[.-]";

	/**
	 * The longest run of digits still read as a number. A component past it is not a
	 * version number anybody wrote, and reading one would overflow rather than answer.
	 */
	private static final int MAX_DIGITS = 9;

	private PluginVersion() {
	}

	/**
	 * @param version the version literal the POM declares, blank when it declares none
	 * @param minimum the oldest version that will do
	 * @return whether {@code version} names a release older than {@code minimum}, and
	 *         {@code false} for a literal that names no number at all
	 */
	static boolean isBelow(String version, String minimum) {
		List<Integer> numbers = numbersOf(version);
		return !numbers.isEmpty() && compare(numbers, numbersOf(minimum)) < 0;
	}

	/**
	 * The components are read until one is not a number, so a qualifier and whatever
	 * follows it are left out rather than compared as text.
	 */
	private static List<Integer> numbersOf(String version) {
		return Stream.of(version.strip().split(SEPARATORS))
				.takeWhile(PluginVersion::isNumber)
				.map(Integer::valueOf)
				.toList();
	}

	private static boolean isNumber(String component) {
		return !component.isEmpty() && component.length() <= MAX_DIGITS
				&& component.chars().allMatch(Character::isDigit);
	}

	/**
	 * A component the shorter version does not carry counts as zero, so {@code 3.1}
	 * and {@code 3.1.0} are one version rather than the shorter being the older.
	 */
	private static int compare(List<Integer> version, List<Integer> minimum) {
		return IntStream.range(0, Math.max(version.size(), minimum.size()))
				.map(index -> Integer.compare(at(version, index), at(minimum, index)))
				.filter(comparison -> comparison != 0)
				.findFirst()
				.orElse(0);
	}

	private static int at(List<Integer> numbers, int index) {
		return index < numbers.size() ? numbers.get(index) : 0;
	}
}

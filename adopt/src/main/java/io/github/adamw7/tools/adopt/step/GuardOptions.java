package io.github.adamw7.tools.adopt.step;

import java.util.List;
import java.util.Optional;

/**
 * What the guard wired into an adopted project is made of: which rule set it
 * runs, which {@code claude-code-enforcer} version it pins, and which
 * {@code CLAUDE.md} sections it demands.
 *
 * <p>The three travel together because the guard and the reshape that must satisfy
 * it are settled from the same answer. A section list that reached the conformer but
 * not the POM is the one shape of bug this area keeps producing: a document reshaped
 * to a contract the guard beside it does not make, found on somebody else's
 * repository after the branch is pushed.
 *
 * @param ruleVersion       the released rule version to pin, or {@code null} to
 *                          resolve the version of the {@code tools} build running
 *                          the adoption — read through {@link #pinnedRuleVersion()}
 * @param rules             how much of the configuration the guard checks
 * @param claudeMdSections  the headings to demand and to conform to, or empty to use
 *                          the ones the detected build system asks for
 */
public record GuardOptions(String ruleVersion, GuardRules rules, List<String> claudeMdSections) {

	public GuardOptions {
		rules = rules == null ? GuardRules.PROJECT : rules;
		claudeMdSections = claudeMdSections == null ? List.of() : List.copyOf(claudeMdSections);
	}

	/** The whole configuration checked, at the running build's rule version, with the build system's sections. */
	public static GuardOptions defaults() {
		return new GuardOptions(null, GuardRules.PROJECT, List.of());
	}

	/** The options a caller who cares only about the pinned version asks for. */
	public static GuardOptions pinning(Optional<String> ruleVersion) {
		return new GuardOptions(ruleVersion.orElse(null), GuardRules.PROJECT, List.of());
	}

	/**
	 * @return the released rule version to pin, or empty to resolve the version of the
	 *         {@code tools} build running the adoption. Preferred over the record's
	 *         own {@link #ruleVersion()}, which answers {@code null} for the same case:
	 *         a field holds the value, and the {@link Optional} is how it is read.
	 */
	public Optional<String> pinnedRuleVersion() {
		return Optional.ofNullable(ruleVersion);
	}

	/**
	 * @param fromBuildSystem what the detected build system's guard demands
	 * @return the sections to demand: the ones the operator named, or the build
	 *         system's when they named none
	 */
	public List<String> sectionsOr(List<String> fromBuildSystem) {
		return claudeMdSections.isEmpty() ? fromBuildSystem : claudeMdSections;
	}
}

package io.github.adamw7.tools.enforcer.project;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * Checks a project's whole Claude Code configuration from its directory alone.
 *
 * <p>The shipped rules are deliberately separate — each names its own inputs and
 * reaches its own verdict — and the cost of that lands on whoever wires them: a
 * project adopting the catalogue writes around twenty elements and sixty paths,
 * every one of them the conventional location the tool already uses. This rule
 * spends that convention instead of restating it:
 *
 * <pre>{@code
 * <claudeCodeProject>
 *   <projectDir>${project.basedir}</projectDir>
 * </claudeCodeProject>
 * }</pre>
 *
 * <p>What it runs is decided by what the project has. A part whose input is absent
 * is skipped rather than failed, because nothing named that path: a project with
 * no {@code .claude/commands} has no slash commands, which is not a fault, and it
 * starts being checked the day it grows one. {@link ProjectParts} says which parts
 * there are and {@link ProjectLayout} where each one looks.
 *
 * <p>Each part still collects its own violations; this rule decides what they
 * mean. So one severity, one baseline and one report cover the whole
 * configuration, and each violation is prefixed with the name of the part that
 * found it — the name to look up in the catalogue, and the name to put in
 * {@code <skippedRules>} to switch that part off. Wiring a rule individually is
 * still supported and still wins where a project needs to configure that rule's
 * own parameters.
 */
@Named("claudeCodeProject")
public class ClaudeCodeProjectRule extends ClaudeCodeEnforcerRule {

	/**
	 * 32 KB, which is a long summary and a short manual — comfortably more than a
	 * {@code CLAUDE.md} that defers detail elsewhere needs, and well under the point
	 * at which loading it into every session is the thing costing the context.
	 */
	static final int DEFAULT_CLAUDE_MD_BUDGET_BYTES = 32768;

	/** The project root every conventional path is resolved against. */
	private File projectDir;

	/**
	 * Names of parts not to run, as the catalogue spells them, e.g.
	 * {@code okfBundleFormat}. Matched without regard to case, since the point of the
	 * parameter is to switch a part off rather than to test the speller.
	 */
	private List<String> skippedRules;

	/**
	 * When true, the parts that can repair what they report do so. It is passed to
	 * the document parts rather than set on this rule's own behalf, because a
	 * composite has no document of its own to repair.
	 */
	private boolean autoFix;

	/**
	 * The size a {@code CLAUDE.md} is held to, since it is loaded into every session
	 * and is meant to be a summary that defers to the fuller document rather than a
	 * copy of it. Zero switches the check off. It is a parameter with a default rather
	 * than a path this rule could look up, because it is the one part of the
	 * convention that is a number instead of a location.
	 */
	private int claudeMdBudgetBytes = DEFAULT_CLAUDE_MD_BUDGET_BYTES;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(projectDir, "projectDir");
		requireProjectDirectory();
		List<String> violations = new ArrayList<>();
		List<ClaudeCodeEnforcerRule> parts = runnableParts();
		logWhatIsChecked(parts);
		for (ClaudeCodeEnforcerRule part : parts) {
			run(part, violations);
		}
		report("The Claude Code project configuration in " + projectDir + " is not valid:", violations);
	}

	/**
	 * Runs one part, turning what it found into violations named after it. A part
	 * that throws is reported as a violation of its own rather than allowed to end
	 * the run: one part's build-setup problem must not cost the operator every other
	 * part's verdict, which is the whole reason to check a project in one pass.
	 */
	private void run(ClaudeCodeEnforcerRule part, List<String> violations) {
		part.setLog(log());
		part.reportTo((ruleName, found) -> found.forEach(violation -> violations.add(named(ruleName, violation))));
		try {
			part.execute();
		} catch (EnforcerRuleException | RuntimeException e) {
			violations.add(named(nameOf(part), "could not be checked: " + e.getMessage()));
		}
	}

	private String named(String ruleName, String violation) {
		return "[" + ruleName + "] " + violation;
	}

	/**
	 * The parts to run: those whose inputs are present, less those the project asked
	 * to skip. Skipping is applied here rather than inside {@link ProjectParts} so
	 * that what a part <em>is</em> and whether this project wants it stay separate
	 * questions.
	 */
	private List<ClaudeCodeEnforcerRule> runnableParts() {
		Set<String> skipped = skippedNames();
		return new ProjectParts(new ProjectLayout(projectDir), autoFix, claudeMdBudgetBytes).present().stream()
				.filter(part -> !skipped.contains(nameOf(part).toLowerCase(Locale.ROOT)))
				.toList();
	}

	private Set<String> skippedNames() {
		if (skippedRules == null) {
			return Set.of();
		}
		return skippedRules.stream().map(name -> name.strip().toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * A project directory that is not there is a build-setup mistake, unlike any of
	 * the paths inside it: those are conventions this rule looked for, while this one
	 * was configured.
	 */
	private void requireProjectDirectory() throws EnforcerRuleException {
		if (!projectDir.isDirectory()) {
			throw new EnforcerRuleException("The projectDir does not exist at " + projectDir);
		}
	}

	/**
	 * Says which parts a run actually covered. Without it a project with no
	 * {@code .claude} directory at all passes exactly like one whose configuration is
	 * spotless, and nothing distinguishes the two.
	 */
	private void logWhatIsChecked(List<ClaudeCodeEnforcerRule> parts) {
		log().debug(() -> "claudeCodeProject is checking " + projectDir + " with "
				+ parts.stream().map(this::nameOf).collect(Collectors.joining(", ")));
		if (parts.isEmpty()) {
			log().warn("claudeCodeProject found no Claude Code configuration to check in " + projectDir);
		}
	}

	/**
	 * A part's catalogue name, derived the way every rule's is — the simple name
	 * without its {@code Rule} suffix, decapitalised.
	 *
	 * @see ClaudeCodeEnforcerRule
	 */
	private String nameOf(ClaudeCodeEnforcerRule part) {
		String simpleName = part.getClass().getSimpleName();
		String withoutSuffix = simpleName.endsWith("Rule")
				? simpleName.substring(0, simpleName.length() - "Rule".length())
				: simpleName;
		return withoutSuffix.substring(0, 1).toLowerCase(Locale.ROOT) + withoutSuffix.substring(1);
	}

	@Override
	protected List<String> howToFix() {
		return List.of(
				"Each violation above is prefixed with the rule that found it; look that rule up in AGENTS.md.",
				"Correct the file or directory each message names.",
				"To stop running one of them, add its name to <skippedRules>.",
				"Re-run the build to confirm the project's configuration is valid.");
	}

	public void setProjectDir(File projectDir) {
		this.projectDir = projectDir;
	}

	public void setSkippedRules(List<String> skippedRules) {
		this.skippedRules = skippedRules;
	}

	public void setAutoFix(boolean autoFix) {
		this.autoFix = autoFix;
	}

	public void setClaudeMdBudgetBytes(int claudeMdBudgetBytes) {
		this.claudeMdBudgetBytes = claudeMdBudgetBytes;
	}
}

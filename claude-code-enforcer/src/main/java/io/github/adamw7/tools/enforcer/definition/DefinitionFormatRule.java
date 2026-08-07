package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.FrontMatter;

/**
 * Base for the enforcer rules that validate one kind of Claude Code definition —
 * a skill, a sub-agent, a slash command — one file at a time. All three scan a
 * configured directory, read each definition, repair its front matter when asked
 * to, and report everything wrong with the lot in one grouped message. They differ
 * only in which entries of the directory carry a definition and in which front
 * matter checks they ask for.
 * <p>
 * The configured directory must exist, because that is a build-setup mistake
 * rather than a content problem; a directory holding no definitions is a pass.
 * <p>
 * Where {@link MultiDefinitionRule} checks a property <em>across</em> every
 * definition of every kind at once, this base checks each definition of one kind
 * on its own.
 */
abstract class DefinitionFormatRule extends ClaudeCodeEnforcerRule {

	/**
	 * How a rule's messages name the definitions it checks. The five strings are one
	 * thing — the vocabulary of a definition kind — so they are supplied together
	 * rather than as five positional arguments a reader has to count.
	 *
	 * @param directoryParameter the configuration parameter naming the directory,
	 *                           e.g. {@code skillsDir}
	 * @param directoryLabel     the directory as a "does not exist" message names
	 *                           it, e.g. {@code Skills}
	 * @param definitionLabel    one definition as a whole-file message names it,
	 *                           e.g. {@code Sub-agent definition}
	 * @param frontMatterLabel   the kind as a front matter message names it, e.g.
	 *                           {@code Sub-agent}
	 * @param header             the header prefixing the grouped report
	 */
	record Naming(String directoryParameter, String directoryLabel, String definitionLabel,
			String frontMatterLabel, String header) {
	}

	private final Naming naming;

	/** When true, a malformed front matter block is rewritten in place instead of failing the build. */
	private boolean autoFix;

	protected DefinitionFormatRule(Naming naming) {
		this.naming = naming;
	}

	@Override
	public final void execute() throws EnforcerRuleException {
		File directory = definitionDir();
		requireConfigured(directory, naming.directoryParameter());
		DefinitionFiles.verifyDirectory(directory, naming.directoryLabel());
		List<String> violations = new ArrayList<>();
		for (File entry : entriesIn(directory)) {
			collectEntryViolations(entry, violations);
		}
		report(naming.header(), violations);
	}

	/** The directory to scan. Injected from the rule configuration. */
	protected abstract File definitionDir();

	/**
	 * The entries of {@code directory} that each carry one definition: its
	 * {@code *.md} files, which is what a command and a sub-agent are. A kind whose
	 * definition is a directory instead — a skill, carrying a {@code SKILL.md} —
	 * says so by overriding.
	 */
	protected File[] entriesIn(File directory) {
		return DefinitionFiles.markdownFiles(directory);
	}

	/** Collects everything wrong with the definition that {@code entry} carries. */
	protected abstract void collectEntryViolations(File entry, List<String> violations);

	/**
	 * The definition's content, auto-fixed when the rule was configured to, or empty
	 * when a violation was collected instead of content worth checking further.
	 */
	protected final Optional<String> contentOf(File file, List<String> violations) {
		return DefinitionContent.of(file, naming.definitionLabel(), autoFix, getLog(), violations);
	}

	/**
	 * The front matter checks for a block this definition kind may leave out, or
	 * empty when it left it out.
	 */
	protected final Optional<FrontMatterChecks> frontMatterOf(String content, File file, List<String> violations) {
		return FrontMatter.parse(content)
				.map(frontMatter -> new FrontMatterChecks(frontMatter, naming.frontMatterLabel(), file, violations));
	}

	/**
	 * The front matter checks for a block this definition kind must declare,
	 * reporting its absence when there is none.
	 */
	protected final Optional<FrontMatterChecks> requiredFrontMatterOf(String content, File file,
			List<String> violations) {
		Optional<FrontMatterChecks> checks = frontMatterOf(content, file, violations);
		if (checks.isEmpty()) {
			violations.add(naming.definitionLabel()
					+ " must start with a YAML front matter block delimited by '---' that a YAML"
					+ " loader can read: " + file);
		}
		return checks;
	}

	void setAutoFix(boolean autoFix) {
		this.autoFix = autoFix;
	}
}

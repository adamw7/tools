package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * A set of already-accepted violations a rule may suppress, so a rule can be
 * turned into an error gate without first fixing every pre-existing violation:
 * only a violation that is not in the baseline fails the build. This lets a team
 * graduate a rule from {@code warn} to {@code error} while cleaning up the backlog
 * behind the gate rather than in one big-bang change.
 * <p>
 * Each accepted violation is stored as one line of its message text; blank lines
 * and lines starting with {@code #} are ignored, so the file can carry comments.
 * Signatures are normalised — the absolute project base directory is replaced with
 * the token {@code ${basedir}} — so a checked-in baseline is portable between a
 * developer's clone and CI. Normalisation is applied both when reading the file
 * and when comparing a live violation, so a hand-written entry with an absolute
 * path still matches.
 * <p>
 * The base directory is the one the rule was configured with, not the build's
 * working directory: Maven runs every module of a reactor from wherever it was
 * invoked, so a working-directory token would resolve to a different prefix
 * depending on whether the build was started from the repository root, from a
 * module directory, or by an IDE — and a baseline recorded under one would quietly
 * suppress nothing under another.
 */
final class Baseline {

	private static final String COMMENT_PREFIX = "#";
	private static final String BASE_DIR_TOKEN = "${basedir}";

	private final Set<String> accepted;
	private final Signatures signatures;

	private Baseline(Set<String> accepted, Signatures signatures) {
		this.accepted = accepted;
		this.signatures = signatures;
	}

	/**
	 * Reads the accepted violations from {@code file}, resolving the
	 * {@code ${basedir}} token against {@code baseDir}. A null or absent file yields
	 * an empty baseline that suppresses nothing, so a rule with no configured
	 * baseline — or one whose baseline has not been recorded yet — behaves exactly
	 * as it did before.
	 */
	static Baseline read(File file, File baseDir) throws EnforcerRuleException {
		Signatures signatures = Signatures.rootedAt(baseDir);
		if (file == null || !file.isFile()) {
			return new Baseline(Set.of(), signatures);
		}
		try {
			Set<String> accepted = new LinkedHashSet<>();
			Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)
					.forEach(line -> addAccepted(accepted, line, signatures));
			return new Baseline(accepted, signatures);
		} catch (IOException e) {
			throw new EnforcerRuleException("Could not read baseline file " + file, e);
		}
	}

	private static void addAccepted(Set<String> accepted, String line, Signatures signatures) {
		String stripped = line.strip();
		if (!stripped.isEmpty() && !stripped.startsWith(COMMENT_PREFIX)) {
			accepted.add(signatures.normalize(stripped));
		}
	}

	/** The violations this baseline does not already accept, in their original order. */
	List<String> newViolations(List<String> violations) {
		return violations.stream()
				.filter(violation -> !accepted.contains(signatures.normalize(violation)))
				.toList();
	}

	/**
	 * The accepted entries that no current violation matches, so a caller can point
	 * out that the baseline has grown stale and those lines can be removed.
	 */
	List<String> staleEntries(List<String> violations) {
		Set<String> current = violations.stream().map(signatures::normalize).collect(Collectors.toSet());
		return accepted.stream().filter(entry -> !current.contains(entry)).toList();
	}

	/**
	 * Writes {@code violations} to {@code file} as a fresh baseline, replacing any
	 * previous content, so a team can record the current violations once and then
	 * gate only new ones. Signatures are normalised against {@code baseDir},
	 * de-duplicated, and sorted so the file stays stable and diffable, and a header
	 * comment explains it. Missing parent directories are created first.
	 */
	static void write(File file, List<String> violations, File baseDir) throws EnforcerRuleException {
		try {
			Path path = file.toPath().toAbsolutePath();
			Files.createDirectories(path.getParent());
			Files.writeString(path, render(violations, Signatures.rootedAt(baseDir)), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new EnforcerRuleException("Could not write baseline file " + file, e);
		}
	}

	private static String render(List<String> violations, Signatures signatures) {
		StringBuilder content = new StringBuilder();
		content.append("# Claude Code enforcer baseline: violations this rule accepts and does not fail on.\n");
		content.append("# Delete a line to make that violation fail the build again; a new violation is never\n");
		content.append("# suppressed. Regenerate by re-running the build with the rule's writeBaseline flag set.\n");
		violations.stream().map(signatures::normalize).distinct().sorted()
				.forEach(line -> content.append(line).append('\n'));
		return content.toString();
	}

	/**
	 * Rewrites the project base directory in a violation message as the
	 * {@code ${basedir}} token, which is what makes a recorded baseline portable.
	 */
	private record Signatures(String base) {

		/** Rooted at {@code baseDir}, or at the working directory when none was configured. */
		static Signatures rootedAt(File baseDir) {
			Path base = baseDir != null ? baseDir.toPath() : Path.of("");
			return new Signatures(base.toAbsolutePath().normalize().toString());
		}

		String normalize(String signature) {
			return signature.replace(base, BASE_DIR_TOKEN);
		}
	}
}

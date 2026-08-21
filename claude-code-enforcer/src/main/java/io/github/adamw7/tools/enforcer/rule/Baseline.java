package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
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
 * working directory: Maven runs every module from wherever it was invoked, so a
 * working-directory token resolves differently depending on whether the build
 * started at the repository root, in a module, or in an IDE — and a baseline
 * recorded under one would quietly suppress nothing under another.
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
	 * Reads the accepted violations from {@code file}, resolving {@code ${basedir}}
	 * against {@code baseDir}. A null or absent file yields an empty baseline, so a
	 * rule with no baseline — or one not yet recorded — behaves as it did before.
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
	 * Writes {@code violations} as a fresh baseline, replacing any previous content.
	 * Signatures are normalised against {@code baseDir}, de-duplicated and sorted so
	 * the file stays diffable. Missing parent directories are created first, unless the
	 * absolutised path has no parent to create — which {@link Path#getParent} answers
	 * {@code null} for at a filesystem root — leaving nothing to make before the write.
	 */
	static void write(File file, List<String> violations, File baseDir) throws EnforcerRuleException {
		try {
			Path path = file.toPath().toAbsolutePath();
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
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
	 * {@code ${basedir}} token, which is what makes a recorded baseline portable, and
	 * folds the message onto one line, which is what makes it storable at all.
	 */
	private record Signatures(String base) {

		/**
		 * Any line break. A baseline stores one violation per line, so one carrying a
		 * break was written as several lines, each read back as an entry no violation
		 * matches — never suppressed, and reported as stale for ever after. A rule builds
		 * its messages on one line but interpolates text it did not write (the entry
		 * {@code permissionsFormat} quotes back is any string the settings file
		 * declared), so the fold belongs here, applied to a live violation and a
		 * recorded entry alike.
		 */
		private static final Pattern LINE_BREAK = Pattern.compile("\\R");

		/** Rooted at {@code baseDir}, or at the working directory when none was configured. */
		static Signatures rootedAt(File baseDir) {
			Path base = baseDir != null ? baseDir.toPath() : Path.of("");
			return new Signatures(base.toAbsolutePath().normalize().toString());
		}

		String normalize(String signature) {
			return LINE_BREAK.matcher(signature.replace(base, BASE_DIR_TOKEN)).replaceAll(" ");
		}
	}
}

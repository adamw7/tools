package io.github.adamw7.tools.enforcer.okf;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.rule.ProjectFiles;
import io.github.adamw7.tools.enforcer.rule.ScanTargets;
import io.github.adamw7.tools.enforcer.rule.Violations;
import io.github.adamw7.tools.enforcer.text.FrontMatter;
import io.github.adamw7.tools.markdown.MarkdownDocument;
import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * Enforcer rule that fails the build when a bundle in Google's Open Knowledge
 * Format is not conformant. OKF is what a repository hands an agent, so a
 * malformed bundle is the same class of problem as a malformed {@code CLAUDE.md}:
 * the file is still there, the consumer just silently gets less than it should.
 * <p>
 * The specification's own conformance conditions are checked unconditionally:
 * <ul>
 * <li>every non-reserved {@code .md} file carries a parseable YAML frontmatter
 * block;</li>
 * <li>every frontmatter block declares a non-empty {@code type}, the one
 * mandatory field;</li>
 * <li>the reserved names keep their defined structure — an {@code index.md}
 * carries no frontmatter beyond an {@code okf_version} at the bundle root, and a
 * {@code log.md} groups its entries under ISO 8601 {@code YYYY-MM-DD}
 * headings.</li>
 * </ul>
 * Where the format defines a closed vocabulary the value is checked too: a
 * {@code status} is {@code draft}, {@code stable} or {@code deprecated}, a
 * {@code stale_after} is a real calendar date, and a {@code generated} mapping
 * names the actor that produced the concept. What the specification leaves open
 * is left open — {@code type} values are not registered centrally, so an unknown
 * one is not a violation.
 * <p>
 * Beyond that, {@code requiredKeys} adds frontmatter keys a project wants on every
 * concept, {@code okfVersion} pins the version the bundle root declares, and
 * {@code requireIndex} demands a listing in every directory holding concepts — off
 * by default, since a consumer must tolerate a missing {@code index.md}. An absent
 * {@code bundleDir} is a pass, so the rule can be wired before a repository emits a
 * bundle. All problems are reported together.
 */
@Named("okfBundleFormat")
public class OkfBundleFormatRule extends ClaudeCodeEnforcerRule {

	private static final String INDEX = "index.md";
	private static final String LOG = "log.md";
	private static final String TYPE_KEY = "type";
	private static final String STATUS_KEY = "status";
	private static final String STALE_AFTER_KEY = "stale_after";
	private static final String GENERATED_KEY = "generated";
	private static final String OKF_VERSION_KEY = "okf_version";
	private static final String SEPARATOR = "/";

	private static final Set<String> STATUSES = Set.of("draft", "stable", "deprecated");

	/** An ISO 8601 calendar date, the only form the specification allows for a date. */
	private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	/** A {@code log.md} entry heading, which the specification defines as a date. */
	private static final Pattern LOG_DATE_HEADING = Pattern.compile("##\\s+(.*)");

	/** The {@code by} entry of a flow mapping such as {@code { by: agent/1, at: ... }}. */
	private static final Pattern GENERATED_BY = Pattern.compile("\\bby\\s*:\\s*\\S");

	/** The bundle root to validate. Injected from the rule configuration. */
	private File bundleDir;

	/** Optional version the bundle root's {@code index.md} must declare, e.g. {@code 0.2}. */
	private String okfVersion;

	/** Optional frontmatter keys every concept must declare beyond the mandatory {@code type}. */
	private List<String> requiredKeys;

	/** When true, every directory holding concepts must also carry an {@code index.md}. */
	private boolean requireIndex;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(bundleDir, "bundleDir");
		report("The OKF bundle at " + bundleDir + " is not conformant:", violations());
	}

	/**
	 * An absent bundle answers no violations rather than returning early, so the pass
	 * still goes through {@link #report} — which is what refreshes a configured
	 * {@code reportFile}. Skipping it left a previous, failing run's HTML report on
	 * disk claiming a rule that had just passed had failed.
	 */
	private List<String> violations() throws EnforcerRuleException {
		if (!bundleDir.isDirectory()) {
			return List.of();
		}
		List<File> documents = markdownFiles();
		List<String> violations = new ArrayList<>();
		documents.forEach(document -> collectDocumentViolations(document, violations));
		collectVersionViolations(documents, violations);
		collectMissingIndexViolations(documents, violations);
		return violations;
	}

	@Override
	protected List<String> howToFix() {
		return List.of(
				"Open each document named above, relative to the bundle root.",
				"Give every concept document a --- delimited YAML frontmatter block "
						+ "declaring a non-empty 'type'.",
				"Keep index.md free of frontmatter, except an okf_version at the bundle root, "
						+ "and give every log.md heading an ISO 8601 YYYY-MM-DD date.",
				"Re-run the build to confirm the bundle is conformant.");
	}

	private List<File> markdownFiles() throws EnforcerRuleException {
		return new ScanTargets(List.of(), List.of(bundleDir))
				.filesInDirectories(ProjectFiles::isMarkdown);
	}

	private void collectDocumentViolations(File document, List<String> violations) {
		String name = relativePath(document);
		Optional<String> content = MarkdownText.readIfText(document);
		if (content.isEmpty()) {
			violations.add(name + " could not be read as UTF-8 text");
			return;
		}
		if (isNamed(document, INDEX)) {
			collectIndexViolations(name, content.get(), violations);
		} else if (isNamed(document, LOG)) {
			collectLogViolations(name, content.get(), violations);
		} else {
			collectConceptViolations(name, content.get(), violations);
		}
	}

	private void collectConceptViolations(String name, String content, List<String> violations) {
		Optional<FrontMatter> parsed = FrontMatter.parse(content);
		if (parsed.isEmpty()) {
			violations.add(name + " has no parseable YAML frontmatter block");
			return;
		}
		FrontMatter frontMatter = parsed.get();
		collectKeyViolations(name, frontMatter, violations);
		collectStatusViolations(name, frontMatter, violations);
		collectDateViolations(name, frontMatter.value(STALE_AFTER_KEY).orElse(null), STALE_AFTER_KEY, violations);
		collectGeneratedViolations(name, frontMatter, violations);
	}

	private void collectKeyViolations(String name, FrontMatter frontMatter, List<String> violations) {
		if (isBlank(frontMatter, TYPE_KEY)) {
			violations.add(name + " declares no non-empty '" + TYPE_KEY + "', the one mandatory OKF field");
		}
		frontMatter.duplicateKeys()
				.forEach(key -> violations.add(name + " declares '" + key + "' more than once"));
		Violations.each(requiredKeys, key -> isBlank(frontMatter, key),
				key -> name + " declares no non-empty '" + key + "'", violations);
	}

	private void collectStatusViolations(String name, FrontMatter frontMatter, List<String> violations) {
		String status = frontMatter.value(STATUS_KEY).orElse(null);
		if (status != null && !STATUSES.contains(status.toLowerCase(Locale.ROOT))) {
			violations.add(name + " declares an unknown '" + STATUS_KEY + "': " + status
					+ " (expected draft, stable or deprecated)");
		}
	}

	private void collectDateViolations(String name, String value, String key, List<String> violations) {
		if (value == null || isDate(value)) {
			return;
		}
		violations.add(name + " declares a '" + key + "' that is not an ISO 8601 date: " + value);
	}

	private void collectGeneratedViolations(String name, FrontMatter frontMatter, List<String> violations) {
		String generated = frontMatter.value(GENERATED_KEY).orElse(null);
		if (generated != null && !GENERATED_BY.matcher(generated).find()) {
			violations.add(name + " declares a '" + GENERATED_KEY + "' without the actor that produced it: "
					+ generated);
		}
	}

	/**
	 * An {@code index.md} is a listing, not a concept, so it declares no frontmatter —
	 * except at the bundle root, where the specification allows an {@code okf_version}.
	 */
	private void collectIndexViolations(String name, String content, List<String> violations) {
		Optional<FrontMatter> parsed = FrontMatter.parse(content);
		if (parsed.isEmpty()) {
			return;
		}
		if (!isBundleRootIndex(name)) {
			violations.add(name + " carries frontmatter; only a bundle-root " + INDEX + " may declare '"
					+ OKF_VERSION_KEY + "'");
			return;
		}
		unexpectedRootKeys(parsed.get())
				.forEach(key -> violations.add(name + " declares '" + key + "'; a bundle-root " + INDEX
						+ " may declare only '" + OKF_VERSION_KEY + "'"));
	}

	private Set<String> unexpectedRootKeys(FrontMatter frontMatter) {
		Set<String> keys = new LinkedHashSet<>(frontMatter.keys());
		keys.remove(OKF_VERSION_KEY);
		return keys;
	}

	private void collectLogViolations(String name, String content, List<String> violations) {
		MarkdownDocument.parse(content).headings().stream()
				.map(LOG_DATE_HEADING::matcher)
				.filter(Matcher::matches)
				.map(matcher -> matcher.group(1).strip())
				.filter(heading -> !isDate(heading))
				.forEach(heading -> violations.add(
						name + " groups entries under '" + heading + "', which is not an ISO 8601 date"));
	}

	/** The declaration is optional in OKF, so configuring {@code okfVersion} is what requires it. */
	private void collectVersionViolations(List<File> documents, List<String> violations) {
		if (okfVersion == null || okfVersion.isBlank()) {
			return;
		}
		File root = documents.stream().filter(document -> isBundleRootIndex(relativePath(document)))
				.findFirst().orElse(null);
		if (root == null) {
			violations.add("The bundle declares no " + INDEX + " at its root, so it cannot declare '"
					+ OKF_VERSION_KEY + ": " + okfVersion + "'");
			return;
		}
		collectDeclaredVersionViolations(root, violations);
	}

	private void collectDeclaredVersionViolations(File root, List<String> violations) {
		String declared = MarkdownText.readIfText(root)
				.flatMap(FrontMatter::parse)
				.flatMap(frontMatter -> frontMatter.value(OKF_VERSION_KEY))
				.orElse("");
		if (!declared.equals(okfVersion)) {
			violations.add(relativePath(root) + " declares '" + OKF_VERSION_KEY + ": " + declared
					+ "' but the build expects " + okfVersion);
		}
	}

	/**
	 * Reports a directory that holds concepts without listing them. A consumer must
	 * tolerate a missing {@code index.md}, so this is only checked when a project
	 * opts in with {@code requireIndex}.
	 */
	private void collectMissingIndexViolations(List<File> documents, List<String> violations) {
		if (!requireIndex) {
			return;
		}
		Set<Path> indexed = documents.stream().filter(document -> isNamed(document, INDEX))
				.map(document -> document.toPath().getParent()).collect(Collectors.toSet());
		documents.stream()
				.filter(document -> !isNamed(document, INDEX) && !isNamed(document, LOG))
				.map(document -> document.toPath().getParent())
				.distinct()
				.filter(directory -> !indexed.contains(directory))
				.forEach(directory -> violations.add(
						relativeDirectory(directory) + " holds concepts but no " + INDEX));
	}

	private boolean isBlank(FrontMatter frontMatter, String key) {
		return frontMatter.value(key).orElse("").isBlank();
	}

	private boolean isNamed(File document, String name) {
		return document.getName().equals(name);
	}

	private boolean isBundleRootIndex(String name) {
		return name.equals(INDEX);
	}

	private boolean isDate(String value) {
		if (!ISO_DATE.matcher(value).matches()) {
			return false;
		}
		try {
			LocalDate.parse(value);
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	/** Bundle-relative and always {@code /}-separated, so a message reads the same on every platform. */
	private String relativePath(File document) {
		return ProjectFiles.normalized(bundleDir)
				.relativize(ProjectFiles.normalized(document))
				.toString().replace(File.separator, SEPARATOR);
	}

	private String relativeDirectory(Path directory) {
		String relative = relativePath(directory.toFile());
		return relative.isEmpty() ? "The bundle root" : relative;
	}

	public void setBundleDir(File bundleDir) {
		this.bundleDir = bundleDir;
	}

	void setOkfVersion(String okfVersion) {
		this.okfVersion = okfVersion;
	}

	void setRequiredKeys(List<String> requiredKeys) {
		this.requiredKeys = requiredKeys;
	}

	void setRequireIndex(boolean requireIndex) {
		this.requireIndex = requireIndex;
	}
}

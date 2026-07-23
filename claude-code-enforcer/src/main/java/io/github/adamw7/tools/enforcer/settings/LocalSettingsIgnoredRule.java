package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that fails the build when {@code .gitignore} does not cover the
 * personal Claude Code settings file. {@code .claude/settings.local.json} holds
 * per-developer overrides — extra permissions, local hooks — and committing it
 * imposes one developer's choices on the whole team, so the durable guard is a
 * gitignore entry that keeps it out of the repository in the first place.
 * <p>
 * The rule parses the configured {@code gitignoreFile} and verifies each path
 * in {@code ignoredPaths} (by default just
 * {@code .claude/settings.local.json}) is matched by it, honouring negations,
 * anchoring, directory patterns, and {@code *}/{@code ?}/{@code **} globs. A
 * path can be covered by any equivalent pattern — the exact path, a
 * {@code *.local.json} glob, or an ignored ancestor directory. All uncovered
 * paths are reported together.
 */
@Named("localSettingsIgnored")
public class LocalSettingsIgnoredRule extends ClaudeCodeEnforcerRule {

	private static final List<String> DEFAULT_IGNORED_PATHS = List.of(".claude/settings.local.json");

	/** The {@code .gitignore} to check, normally the repository root's. Injected from the rule configuration. */
	private File gitignoreFile;

	/** Optional override for the repository-relative paths that must be ignored. */
	private List<String> ignoredPaths;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(gitignoreFile, "gitignoreFile");
		requireExists(gitignoreFile, ".gitignore");
		Gitignore gitignore = Gitignore.parse(MarkdownText.read(gitignoreFile, ".gitignore"));
		List<String> violations = new ArrayList<>();
		for (String path : ignoredPaths()) {
			collectUncoveredPath(gitignore, path, violations);
		}
		report("Local settings are not ignored:", violations);
	}

	@Override
	protected List<String> howToFix() {
		return List.of(
				"Open " + gitignoreFile + ".",
				"Add a line covering each path listed above, e.g. the path itself verbatim.",
				"If the file was already committed, remove it from the index with 'git rm --cached <path>'.",
				"Re-run the build to confirm the local settings stay out of the repository.");
	}

	private void collectUncoveredPath(Gitignore gitignore, String path, List<String> violations) {
		String normalized = normalized(path);
		if (!gitignore.covers(normalized)) {
			violations.add(gitignoreFile + " does not cover: " + normalized);
		}
	}

	/** Normalises a configured path to the {@code /}-separated, unanchored form the matcher expects. */
	private String normalized(String path) {
		String slashed = path.replace('\\', '/');
		String withoutDot = slashed.startsWith("./") ? slashed.substring(2) : slashed;
		return withoutDot.startsWith("/") ? withoutDot.substring(1) : withoutDot;
	}

	private List<String> ignoredPaths() {
		return ignoredPaths != null ? ignoredPaths : DEFAULT_IGNORED_PATHS;
	}

	void setGitignoreFile(File gitignoreFile) {
		this.gitignoreFile = gitignoreFile;
	}

	void setIgnoredPaths(List<String> ignoredPaths) {
		this.ignoredPaths = ignoredPaths;
	}

	@Override
	public String toString() {
		return String.format("LocalSettingsIgnoredRule[gitignoreFile=%s]", gitignoreFile);
	}
}

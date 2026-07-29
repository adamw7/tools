package io.github.adamw7.tools.enforcer.secret;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.rule.ScanTargets;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that fails the build when a configured file contains what looks
 * like a literal credential. Claude Code configuration is a natural place for a
 * key to leak — an API token pasted into a {@code .mcp.json} {@code env} or
 * {@code headers} block, a {@code settings.json} environment variable, or a hook
 * script — and once committed it must be rotated, so the cheapest fix is to refuse
 * the commit's build.
 * <p>
 * The scanned targets are the configured {@code files} plus every regular file
 * under the configured {@code directories}; an absent target is skipped, because
 * most of these files are optional, and so is a file that cannot be decoded as
 * text. Each match is reported with its file, line, and the kind of credential it
 * resembles, but only the first characters of the match itself, so the report
 * never republishes the secret it found. The default patterns cover common API
 * token formats (Anthropic, AWS, GitHub, Slack, private key blocks); custom
 * {@code secretPatterns} are scanned in addition, or instead when
 * {@code useDefaultPatterns} is switched off.
 */
@Named("noSecrets")
public class NoSecretsRule extends ClaudeCodeEnforcerRule {

	private static final int VISIBLE_PREFIX_LENGTH = 8;

	/** Files to scan. An entry that does not exist is skipped, since most targets are optional. */
	private List<File> files;

	/** Directories whose regular files are scanned recursively. An absent directory is skipped. */
	private List<File> directories;

	/** Additional regular expressions to scan for, each reported under its own pattern text. */
	private List<String> secretPatterns;

	/** When true (default), the built-in credential patterns are scanned as well. */
	private boolean useDefaultPatterns = true;

	@Override
	public void execute() throws EnforcerRuleException {
		List<SecretPattern> patterns = patterns();
		ScanTargets targets = new ScanTargets(files, directories);
		targets.requireConfigured();
		requirePatterns(patterns);
		List<String> violations = new ArrayList<>();
		for (File file : targets.allFiles()) {
			scanIfPresent(file, patterns, violations);
		}
		report("Files contain what look like secrets:", violations);
	}

	@Override
	protected List<String> howToFix() {
		return List.of(
				"Open each file listed above at the reported line.",
				"Replace the literal credential with an environment variable expansion such as ${API_KEY}.",
				"Rotate the leaked credential — a value that reached the working tree may already be compromised.",
				"Re-run the build to confirm no secrets remain.");
	}

	private void requirePatterns(List<SecretPattern> patterns) throws EnforcerRuleException {
		if (patterns.isEmpty()) {
			throw new EnforcerRuleException(
					"Configure secretPatterns or leave useDefaultPatterns enabled, so there is something to scan for");
		}
	}

	private void scanIfPresent(File file, List<SecretPattern> patterns, List<String> violations) {
		if (!file.isFile()) {
			return;
		}
		List<String> lines = readTextLines(file);
		for (int i = 0; i < lines.size(); i++) {
			scanLine(file, lines.get(i), i + 1, patterns, violations);
		}
	}

	private void scanLine(File file, String line, int lineNumber, List<SecretPattern> patterns,
			List<String> violations) {
		for (SecretPattern pattern : patterns) {
			collectMatches(file, line, lineNumber, pattern, violations);
		}
	}

	private void collectMatches(File file, String line, int lineNumber, SecretPattern pattern,
			List<String> violations) {
		Matcher matcher = pattern.pattern().matcher(line);
		while (matcher.find()) {
			violations.add(file + " line " + lineNumber + " contains what looks like a " + pattern.name()
					+ ": " + masked(matcher.group()));
		}
	}

	/** The first characters of the match followed by an ellipsis, so the report never echoes the full secret. */
	private String masked(String match) {
		return match.substring(0, Math.min(VISIBLE_PREFIX_LENGTH, match.length())) + "...";
	}

	/** The file's lines, or none when it cannot be decoded as text (e.g. a binary asset). */
	private List<String> readTextLines(File file) {
		Optional<String> content = MarkdownText.readIfText(file);
		if (content.isEmpty()) {
			getLog().debug("Skipping undecodable file " + file);
		}
		return content.map(text -> text.lines().toList()).orElseGet(List::of);
	}

	private List<SecretPattern> patterns() throws EnforcerRuleException {
		List<SecretPattern> patterns = new ArrayList<>();
		if (useDefaultPatterns) {
			patterns.addAll(SecretPattern.defaults());
		}
		List<String> configured = secretPatterns != null ? secretPatterns : List.of();
		for (String regex : configured) {
			patterns.add(compiled(regex));
		}
		return patterns;
	}

	/**
	 * A configured pattern that is not a valid regular expression is a build-setup
	 * mistake, so it fails with a message naming it rather than letting a
	 * {@link PatternSyntaxException} escape as an internal build error.
	 */
	private SecretPattern compiled(String regex) throws EnforcerRuleException {
		try {
			return SecretPattern.of(regex, regex);
		} catch (PatternSyntaxException e) {
			throw new EnforcerRuleException(
					"secretPattern '" + regex + "' is not a valid regular expression: " + e.getDescription());
		}
	}

	void setFiles(List<File> files) {
		this.files = files;
	}

	void setDirectories(List<File> directories) {
		this.directories = directories;
	}

	void setSecretPatterns(List<String> secretPatterns) {
		this.secretPatterns = secretPatterns;
	}

	void setUseDefaultPatterns(boolean useDefaultPatterns) {
		this.useDefaultPatterns = useDefaultPatterns;
	}
}

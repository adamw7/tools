package io.github.adamw7.tools.enforcer.secret;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * Enforcer rule that fails the build when a configured file contains what looks
 * like a literal credential. Claude Code configuration is a natural place for a
 * key to leak — an API token pasted into a {@code .mcp.json} {@code env} or
 * {@code headers} block, a {@code settings.json} environment variable, or a
 * hook script — and once committed it must be rotated, so the cheapest fix is to
 * refuse the commit's build. The scanned targets are the configured
 * {@code files} plus every regular file under the configured
 * {@code directories}; an absent target is skipped, because most of these files
 * are optional.
 * <p>
 * Each match is reported with its file, line, and the kind of credential it
 * resembles, but only the first characters of the match itself, so the report
 * never republishes the secret it found. The default patterns cover common API
 * token formats (Anthropic, AWS, GitHub, Slack, private key blocks); custom
 * {@code secretPatterns} regular expressions are scanned in addition, or
 * instead when {@code useDefaultPatterns} is switched off. A file that cannot
 * be decoded as text (e.g. a binary asset) is skipped. All problems found are
 * reported together — the fix is to move the value into an environment variable
 * expansion such as {@code ${API_KEY}} and rotate the leaked credential.
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
		requireTargetsConfigured();
		requirePatterns(patterns);
		List<String> violations = new ArrayList<>();
		for (File file : configuredFiles()) {
			scanIfPresent(file, patterns, violations);
		}
		for (File directory : configuredDirectories()) {
			scanDirectory(directory, patterns, violations);
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

	private void requireTargetsConfigured() throws EnforcerRuleException {
		if (configuredFiles().isEmpty() && configuredDirectories().isEmpty()) {
			throw new EnforcerRuleException("Configure at least one of the files or directories parameters");
		}
	}

	private void requirePatterns(List<SecretPattern> patterns) throws EnforcerRuleException {
		if (patterns.isEmpty()) {
			throw new EnforcerRuleException(
					"Configure secretPatterns or leave useDefaultPatterns enabled, so there is something to scan for");
		}
	}

	private void scanDirectory(File directory, List<SecretPattern> patterns, List<String> violations) {
		if (!directory.isDirectory()) {
			return;
		}
		for (Path file : regularFilesIn(directory)) {
			scanIfPresent(file.toFile(), patterns, violations);
		}
	}

	private List<Path> regularFilesIn(File directory) {
		try (Stream<Path> walk = Files.walk(directory.toPath())) {
			return walk.filter(Files::isRegularFile).sorted().toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not scan directory " + directory, e);
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
		try {
			return Files.readString(file.toPath()).lines().toList();
		} catch (IOException e) {
			getLog().debug("Skipping undecodable file " + file + ": " + e.getMessage());
			return List.of();
		}
	}

	private List<SecretPattern> patterns() {
		List<SecretPattern> patterns = new ArrayList<>();
		if (useDefaultPatterns) {
			patterns.addAll(SecretPattern.defaults());
		}
		for (String regex : configuredSecretPatterns()) {
			patterns.add(SecretPattern.of(regex, regex));
		}
		return patterns;
	}

	private List<File> configuredFiles() {
		return files != null ? files : List.of();
	}

	private List<File> configuredDirectories() {
		return directories != null ? directories : List.of();
	}

	private List<String> configuredSecretPatterns() {
		return secretPatterns != null ? secretPatterns : List.of();
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

	@Override
	public String toString() {
		return String.format("NoSecretsRule[files=%s, directories=%s]", files, directories);
	}
}

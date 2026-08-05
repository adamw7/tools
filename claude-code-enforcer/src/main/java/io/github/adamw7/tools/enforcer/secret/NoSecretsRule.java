package io.github.adamw7.tools.enforcer.secret;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.rule.Patterns;
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
	private static final String SECRET_PATTERN_PARAMETER = "secretPattern";

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
		List<CredentialPattern> patterns = patterns();
		ScanTargets targets = new ScanTargets(files, directories);
		targets.requireConfigured();
		requirePatterns(patterns);
		List<String> violations = targets.allFiles().stream()
				.filter(File::isFile)
				.flatMap(file -> scan(file, patterns))
				.toList();
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

	private void requirePatterns(List<CredentialPattern> patterns) throws EnforcerRuleException {
		if (patterns.isEmpty()) {
			throw new EnforcerRuleException(
					"Configure secretPatterns or leave useDefaultPatterns enabled, so there is something to scan for");
		}
	}

	private Stream<String> scan(File file, List<CredentialPattern> patterns) {
		List<String> lines = readTextLines(file);
		return IntStream.range(0, lines.size())
				.boxed()
				.flatMap(index -> scanLine(file, lines.get(index), index + 1, patterns));
	}

	private Stream<String> scanLine(File file, String line, int lineNumber, List<CredentialPattern> patterns) {
		return patterns.stream().flatMap(pattern -> matches(file, line, lineNumber, pattern));
	}

	private Stream<String> matches(File file, String line, int lineNumber, CredentialPattern pattern) {
		return pattern.pattern().matcher(line)
				.results()
				.map(match -> file + " line " + lineNumber + " contains what looks like a " + pattern.name()
						+ ": " + masked(match.group()));
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

	/** A configured pattern is reported under its own text, since only its author can name it. */
	private List<CredentialPattern> patterns() throws EnforcerRuleException {
		List<CredentialPattern> patterns = new ArrayList<>();
		if (useDefaultPatterns) {
			patterns.addAll(CredentialPattern.defaults());
		}
		for (String regex : secretPatterns != null ? secretPatterns : List.<String>of()) {
			patterns.add(new CredentialPattern(regex, Patterns.compile(regex, SECRET_PATTERN_PARAMETER)));
		}
		return patterns;
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

package io.github.adamw7.tools.enforcer.secret;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A named credential shape the {@link NoSecretsRule} scans for. The default
 * catalogue covers the token formats most likely to land in Claude Code
 * configuration — API keys pasted into an {@code env} block or a hook script —
 * and each carries a human-readable name so a violation says what kind of
 * secret it looks like without echoing the secret itself.
 * <p>
 * Deliberately <em>not</em> named after the {@code secretPatterns} parameter it
 * serves: Plexus resolves a configured list's element type from the child element
 * name, trying the rule's own package first, so a {@code SecretPattern} class would
 * capture every {@code <secretPattern>} element and fail the build instantiating it.
 */
record CredentialPattern(String name, Pattern pattern) {

	static CredentialPattern of(String name, String regex) {
		return new CredentialPattern(name, Pattern.compile(regex));
	}

	/** The built-in credential shapes, scanned unless {@code useDefaultPatterns} is switched off. */
	static List<CredentialPattern> defaults() {
		return List.of(
				of("Anthropic API key", "sk-ant-[A-Za-z0-9_-]{16,}"),
				of("AWS access key ID", "(?<![A-Z0-9])AKIA[0-9A-Z]{16}(?![A-Z0-9])"),
				of("GitHub token", "(?<![A-Za-z0-9])gh[pousr]_[A-Za-z0-9]{36,}"),
				of("GitHub fine-grained token", "github_pat_[A-Za-z0-9_]{22,}"),
				of("Slack token", "xox[abpr]-[A-Za-z0-9-]{10,}"),
				of("private key block", "-----BEGIN [A-Z ]*PRIVATE KEY-----"));
	}
}

package io.github.adamw7.tools.enforcer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Enforcer rule that keeps two documents from contradicting each other. Because
 * {@code CLAUDE.md} defers to {@code AGENTS.md} as the single source of truth,
 * any fact stated in both must agree. Each configured pattern is a regular
 * expression with one capturing group; the rule captures that group from each
 * file and fails when the captured values differ, or when the fact appears in
 * one file but not the other.
 * <p>
 * For example the pattern {@code Java (\d+)} pins the Java version: if
 * {@code CLAUDE.md} says {@code Java 25} and {@code AGENTS.md} says
 * {@code Java 24}, the build fails. A pattern that matches in neither file is
 * ignored, so unrelated documents are unaffected. All mismatches are reported
 * together.
 */
@Named("crossDocConsistency")
public class CrossDocConsistencyRule extends ClaudeCodeEnforcerRule {

	/** The first document to compare. Injected from the rule configuration. */
	private File claudeMdFile;

	/** The second document to compare. Injected from the rule configuration. */
	private File agentsMdFile;

	/** Regular expressions, each with one capturing group, whose captured value must agree. */
	private List<String> consistentPatterns;

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		String first = readContent(claudeMdFile);
		String second = readContent(agentsMdFile);
		List<String> violations = new ArrayList<>();
		for (String pattern : patterns()) {
			collectPatternViolations(pattern, first, second, violations);
		}
		report("Documents are inconsistent:", violations);
	}

	private void verifyConfigured() throws EnforcerRuleException {
		verifyConfigured(claudeMdFile, "claudeMdFile");
		verifyConfigured(agentsMdFile, "agentsMdFile");
	}

	private void verifyConfigured(File file, String parameter) throws EnforcerRuleException {
		if (file == null) {
			throw new EnforcerRuleException("The " + parameter + " parameter is not configured");
		}
		if (!file.isFile()) {
			throw new EnforcerRuleException(parameter + " does not exist at " + file);
		}
	}

	private void collectPatternViolations(String pattern, String first, String second, List<String> violations) {
		Pattern compiled = Pattern.compile(pattern);
		Optional<String> firstValue = capture(compiled, first);
		Optional<String> secondValue = capture(compiled, second);
		if (firstValue.isEmpty() && secondValue.isEmpty()) {
			return;
		}
		addMismatchViolation(pattern, firstValue, secondValue, violations);
	}

	private void addMismatchViolation(String pattern, Optional<String> firstValue, Optional<String> secondValue,
			List<String> violations) {
		if (!firstValue.equals(secondValue)) {
			violations.add("pattern '" + pattern + "' captured " + describe(claudeMdFile, firstValue) + " but "
					+ describe(agentsMdFile, secondValue));
		}
	}

	private String describe(File file, Optional<String> value) {
		return file.getName() + "=" + value.map(captured -> "'" + captured + "'").orElse("<absent>");
	}

	private Optional<String> capture(Pattern pattern, String content) {
		Matcher matcher = pattern.matcher(content);
		return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	private String readContent(File file) {
		try {
			return MarkdownText.stripByteOrderMark(Files.readString(file.toPath()));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + file, e);
		}
	}

	private List<String> patterns() {
		return consistentPatterns != null ? consistentPatterns : List.of();
	}

	void setClaudeMdFile(File claudeMdFile) {
		this.claudeMdFile = claudeMdFile;
	}

	void setAgentsMdFile(File agentsMdFile) {
		this.agentsMdFile = agentsMdFile;
	}

	void setConsistentPatterns(List<String> consistentPatterns) {
		this.consistentPatterns = consistentPatterns;
	}

	@Override
	public String toString() {
		return String.format("CrossDocConsistencyRule[claudeMdFile=%s, agentsMdFile=%s]", claudeMdFile, agentsMdFile);
	}
}

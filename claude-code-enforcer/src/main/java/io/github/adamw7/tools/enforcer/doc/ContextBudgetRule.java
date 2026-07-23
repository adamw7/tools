package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that keeps agent context files within a size budget.
 * {@code CLAUDE.md} is loaded into every Claude Code session, and each skill,
 * sub-agent, and command definition is loaded whenever it triggers, so an
 * unbounded file quietly taxes every conversation. The rule measures every
 * configured file (each must exist) and every {@code *.md} file under the
 * configured directories (an absent directory is skipped) against up to three
 * budgets: {@code maxBytes} (on-disk size), {@code maxLines}, and
 * {@code maxTokens} — the latter estimated with the common four-characters-per-
 * token heuristic, which is deliberately rough but stable enough for a budget.
 * <p>
 * A budget left at zero is disabled; at least one must be configured, because a
 * rule with no limits is a build-setup mistake. All files over budget are
 * reported together — the fix is to move detail into AGENTS.md or an
 * on-demand skill rather than the always-loaded context.
 */
@Named("contextBudget")
public class ContextBudgetRule extends ClaudeCodeEnforcerRule {

	private static final String MARKDOWN_EXTENSION = ".md";
	private static final int CHARS_PER_TOKEN = 4;

	/** Files that must fit the budget. Each configured file must exist. */
	private List<File> files;

	/** Directories whose {@code *.md} files must fit the budget. An absent directory is skipped. */
	private List<File> directories;

	/** Maximum on-disk size in bytes. Zero (default) disables the check. */
	private long maxBytes;

	/** Maximum number of lines. Zero (default) disables the check. */
	private int maxLines;

	/** Maximum estimated tokens (characters divided by four). Zero (default) disables the check. */
	private int maxTokens;

	@Override
	public void execute() throws EnforcerRuleException {
		requireLimitConfigured();
		requireTargetsConfigured();
		List<String> violations = new ArrayList<>();
		for (File file : configuredFiles()) {
			requireExists(file, file.getName());
			collectBudgetViolations(file, violations);
		}
		for (File directory : configuredDirectories()) {
			collectDirectoryViolations(directory, violations);
		}
		report("Context budget exceeded:", violations);
	}

	@Override
	protected List<String> howToFix() {
		return List.of(
				"Open each file listed above.",
				"Move detail that is not needed in every session into AGENTS.md or an on-demand skill.",
				"Re-run the build to confirm every context file fits its budget.");
	}

	private void requireLimitConfigured() throws EnforcerRuleException {
		if (maxBytes <= 0 && maxLines <= 0 && maxTokens <= 0) {
			throw new EnforcerRuleException("Configure at least one of maxBytes, maxLines, or maxTokens");
		}
	}

	private void requireTargetsConfigured() throws EnforcerRuleException {
		if (configuredFiles().isEmpty() && configuredDirectories().isEmpty()) {
			throw new EnforcerRuleException("Configure at least one of the files or directories parameters");
		}
	}

	private void collectDirectoryViolations(File directory, List<String> violations) {
		if (!directory.isDirectory()) {
			return;
		}
		for (Path file : markdownFilesIn(directory)) {
			collectBudgetViolations(file.toFile(), violations);
		}
	}

	private List<Path> markdownFilesIn(File directory) {
		try (Stream<Path> walk = Files.walk(directory.toPath())) {
			return walk.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(MARKDOWN_EXTENSION))
					.sorted().toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not scan directory " + directory, e);
		}
	}

	private void collectBudgetViolations(File file, List<String> violations) {
		collectBytesViolation(file, violations);
		if (maxLines > 0 || maxTokens > 0) {
			collectContentViolations(file, MarkdownText.read(file, file.getName()), violations);
		}
	}

	private void collectBytesViolation(File file, List<String> violations) {
		if (maxBytes > 0 && file.length() > maxBytes) {
			violations.add(file + " is " + file.length() + " bytes, over the " + maxBytes + "-byte budget");
		}
	}

	private void collectContentViolations(File file, String content, List<String> violations) {
		long lines = content.lines().count();
		if (maxLines > 0 && lines > maxLines) {
			violations.add(file + " has " + lines + " lines, over the " + maxLines + "-line budget");
		}
		collectTokensViolation(file, content, violations);
	}

	private void collectTokensViolation(File file, String content, List<String> violations) {
		long tokens = estimatedTokens(content);
		if (maxTokens > 0 && tokens > maxTokens) {
			violations.add(file + " is an estimated " + tokens + " tokens, over the " + maxTokens
					+ "-token budget");
		}
	}

	/** Rounds up, so a one-character file estimates to one token rather than zero. */
	private long estimatedTokens(String content) {
		return (content.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
	}

	private List<File> configuredFiles() {
		return files != null ? files : List.of();
	}

	private List<File> configuredDirectories() {
		return directories != null ? directories : List.of();
	}

	void setFiles(List<File> files) {
		this.files = files;
	}

	void setDirectories(List<File> directories) {
		this.directories = directories;
	}

	void setMaxBytes(long maxBytes) {
		this.maxBytes = maxBytes;
	}

	void setMaxLines(int maxLines) {
		this.maxLines = maxLines;
	}

	void setMaxTokens(int maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public String toString() {
		return String.format("ContextBudgetRule[files=%s, directories=%s]", files, directories);
	}
}

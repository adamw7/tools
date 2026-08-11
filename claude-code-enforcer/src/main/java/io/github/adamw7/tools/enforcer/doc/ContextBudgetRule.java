package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.rule.ProjectFiles;
import io.github.adamw7.tools.enforcer.rule.ScanTargets;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that keeps agent context files within a size budget.
 * {@code CLAUDE.md} is loaded into every Claude Code session, and each skill,
 * sub-agent, and command definition is loaded whenever it triggers, so an
 * unbounded file quietly taxes every conversation.
 * <p>
 * Every configured file (each must exist) and every {@code *.md} file under the
 * configured directories (an absent directory is skipped) is measured against up
 * to three budgets: {@code maxBytes} (on-disk size), {@code maxLines}, and
 * {@code maxTokens} — estimated with the common four-characters-per-token
 * heuristic, deliberately rough but stable enough for a budget. A budget left at
 * zero is disabled, and at least one must be configured. All files over budget are
 * reported together; the fix is to move detail into AGENTS.md or an on-demand
 * skill rather than the always-loaded context.
 */
@Named("contextBudget")
public class ContextBudgetRule extends ClaudeCodeEnforcerRule {

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
		ScanTargets targets = new ScanTargets(files, directories);
		targets.requireConfigured();
		for (File file : targets.files()) {
			requireExists(file, file.getName());
		}
		List<String> violations = new ArrayList<>();
		for (File file : targets.allFiles(ProjectFiles::isMarkdown)) {
			collectBudgetViolations(file, violations);
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

	private void collectBudgetViolations(File file, List<String> violations) {
		if (maxBytes > 0 && file.length() > maxBytes) {
			violations.add(file + " is " + file.length() + " bytes, over the " + maxBytes + "-byte budget");
		}
		if (maxLines > 0 || maxTokens > 0) {
			collectDecodedViolations(file, violations);
		}
	}

	/**
	 * The line and token budgets need the file's text, so a file that cannot be
	 * decoded is reported rather than read. Only the byte budget applies to it, and
	 * an undecodable file must not abort the build before the rest are measured.
	 */
	private void collectDecodedViolations(File file, List<String> violations) {
		Optional<String> content = MarkdownText.readIfText(file);
		if (content.isEmpty()) {
			violations.add(file + " cannot be read as text, so its line and token budgets cannot be measured");
			return;
		}
		collectContentViolations(file, content.get(), violations);
	}

	private void collectContentViolations(File file, String content, List<String> violations) {
		long lines = content.lines().count();
		if (maxLines > 0 && lines > maxLines) {
			violations.add(file + " has " + lines + " lines, over the " + maxLines + "-line budget");
		}
		long tokens = estimatedTokens(content);
		if (maxTokens > 0 && tokens > maxTokens) {
			violations.add(file + " is an estimated " + tokens + " tokens, over the " + maxTokens + "-token budget");
		}
	}

	/** Rounds up, so a one-character file estimates to one token rather than zero. */
	private long estimatedTokens(String content) {
		return (content.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
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
}

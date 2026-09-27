package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.rule.ProjectFiles;
import io.github.adamw7.tools.enforcer.rule.ScanTargets;
import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * Enforcer rule that fails the build when a hook script under
 * {@code .claude/hooks} is not a well-formed executable, or — when asked — when
 * no command hook of {@code .claude/settings.json} runs it.
 * <p>
 * Where {@link HookCommandsValidRule} validates the JSON shape of the
 * {@code hooks} section, this rule validates the scripts themselves: every regular
 * file under {@code hooksDir}, at any depth, must be non-empty, start with a
 * {@code #!} shebang line, and carry the executable bit, so a hook Claude Code
 * would try to run cannot be committed broken. Each script check can be switched off
 * ({@code requireShebang}, {@code requireExecutable}), and an optional
 * {@code allowedExtensions} whitelist rejects a stray file such as a {@code .txt}
 * note left in the directory.
 * <p>
 * With {@code reportUnreferencedScripts}, a script in the directory that no command
 * hook of {@code settingsFile} runs is reported too — a hook resolving a
 * project-local path into the hooks directory, written with
 * {@code $CLAUDE_PROJECT_DIR} or as the repository-relative path Claude Code
 * resolves the same way, counts as running it. A hook naming a script that does not
 * exist is {@link HookCommandsValidRule}'s to report, which resolves every
 * project-local script a hook runs. {@code hooksDir} must be configured, but an
 * absent directory is a pass since hooks are optional; a path that is there and is
 * not a directory fails, a rule that silently scanned nothing being
 * indistinguishable from a project with no hooks. A {@code settingsFile} the report
 * was asked to read that is absent fails outright as a build-setup mistake. All
 * problems are reported together.
 */
@Named("hooksFormat")
public class HooksFormatRule extends ClaudeCodeEnforcerRule {

	private static final String SHEBANG = "#!";

	/** The {@code .claude/hooks} directory to scan. Injected from the rule configuration. */
	private File hooksDir;

	/** The {@code .claude/settings.json} whose command hooks {@code reportUnreferencedScripts} reads. */
	private File settingsFile;

	/** Base directory that {@code $CLAUDE_PROJECT_DIR} resolves to. Defaults to the settings file's grandparent. */
	private File projectDir;

	/** Optional whitelist of file extensions a hook script may use, e.g. {@code sh}, {@code py}. */
	private List<String> allowedExtensions;

	/** When true (default), each hook script must start with a {@code #!} shebang line. */
	private boolean requireShebang = true;

	/** When true (default), each hook script must carry the executable bit. */
	private boolean requireExecutable = true;

	/** When true, a script in the directory that no command hook of {@code settingsFile} runs is reported. */
	private boolean reportUnreferencedScripts;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(hooksDir, "hooksDir");
		ProjectFiles.requireDirectoryOrAbsent(hooksDir, "Hooks");
		List<File> scripts = scripts();
		log().debug(() -> "Hooks: checking " + scripts.size() + " script(s) under " + hooksDir);
		List<String> violations = new ArrayList<>();
		for (File script : scripts) {
			collectScriptViolations(script, violations);
		}
		collectWiringViolations(scripts, violations);
		report("Hook scripts are not well formed:", violations);
	}

	/**
	 * Every regular file under the hooks directory, however deep. Stopping at the top
	 * level left the two halves of this rule disagreeing about what that directory
	 * is: {@link HookWiring} requires a hook naming {@code hooks/setup/install.sh} to
	 * exist, while the format checks never saw it, so that script could be committed
	 * with no shebang and no executable bit.
	 */
	private List<File> scripts() throws EnforcerRuleException {
		return new ScanTargets(List.of(), List.of(hooksDir)).filesInDirectories(path -> true);
	}

	/**
	 * A file that cannot be decoded as text is reported rather than read: the hooks
	 * directory holds whatever a repository put there, and an undecodable file must
	 * not abort the build before the remaining scripts are checked.
	 */
	private void collectScriptViolations(File script, List<String> violations) {
		Optional<String> text = MarkdownText.readIfTextWithByteOrderMark(script);
		if (text.isEmpty()) {
			violations.add("hook script cannot be read as a text script: " + script);
			return;
		}
		String raw = text.get();
		if (MarkdownText.stripByteOrderMark(raw).isBlank()) {
			violations.add("hook script is empty: " + script);
			return;
		}
		if (requireShebang) {
			collectShebangViolation(script, raw, violations);
		}
		if (requireExecutable && !script.canExecute()) {
			violations.add("hook script is not executable: " + script);
		}
		if (allowedExtensions != null && !allowedExtensions.contains(extensionOf(script))) {
			violations.add("hook script has a disallowed extension: " + script);
		}
	}

	/**
	 * The shebang is read from the raw text, byte-order mark and all: the kernel
	 * reads bytes, so a mark in front of the {@code #!} makes the script unrunnable,
	 * and reading the stripped text reported exactly that script as well formed. A
	 * script with no shebang at all is still reported as the missing shebang it is.
	 */
	private void collectShebangViolation(File script, String raw, List<String> violations) {
		if (!MarkdownText.stripByteOrderMark(raw).startsWith(SHEBANG)) {
			violations.add("hook script must start with a '#!' shebang line: " + script);
		} else if (MarkdownText.startsWithByteOrderMark(raw)) {
			violations.add("hook script has a byte-order mark before its '#!' shebang line, so it cannot be run: "
					+ script);
		}
	}

	private String extensionOf(File script) {
		String name = script.getName();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1);
	}

	/**
	 * A settings file the report was asked to read that is not there is a build-setup
	 * mistake and fails whatever the severity: reporting it as a violation let
	 * {@code severity=warn} turn the whole report off silently. What the report itself
	 * finds is a violation, collected by {@link HookWiring}.
	 */
	private void collectWiringViolations(List<File> scripts, List<String> violations) throws EnforcerRuleException {
		if (!reportUnreferencedScripts || settingsFile == null) {
			return;
		}
		requireExists(settingsFile, "settings.json");
		new HookWiring(hooksDir, settingsFile, projectDir).collectViolations(scripts, violations);
	}

	public void setHooksDir(File hooksDir) {
		this.hooksDir = hooksDir;
	}

	public void setSettingsFile(File settingsFile) {
		this.settingsFile = settingsFile;
	}

	public void setProjectDir(File projectDir) {
		this.projectDir = projectDir;
	}

	void setAllowedExtensions(List<String> allowedExtensions) {
		this.allowedExtensions = allowedExtensions;
	}

	void setRequireShebang(boolean requireShebang) {
		this.requireShebang = requireShebang;
	}

	void setRequireExecutable(boolean requireExecutable) {
		this.requireExecutable = requireExecutable;
	}

	void setReportUnreferencedScripts(boolean reportUnreferencedScripts) {
		this.reportUnreferencedScripts = reportUnreferencedScripts;
	}
}

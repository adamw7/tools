package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that fails the build when a hook script under
 * {@code .claude/hooks} is not a well-formed executable, or when
 * {@code .claude/settings.json} wires a command hook to a script that should live
 * in that directory but does not.
 * <p>
 * Where {@link HookCommandsValidRule} validates the JSON shape of the
 * {@code hooks} section, this rule validates the scripts themselves: every regular
 * file directly under {@code hooksDir} must be non-empty, start with a {@code #!}
 * shebang line, and carry the executable bit, so a hook Claude Code would try to
 * run cannot be committed broken. Each script check can be switched off
 * ({@code requireShebang}, {@code requireExecutable}), and an optional
 * {@code allowedExtensions} whitelist rejects a stray file such as a {@code .txt}
 * note left in the directory.
 * <p>
 * When {@code settingsFile} is configured, any command hook whose command resolves
 * a project-local path into the hooks directory — written with
 * {@code $CLAUDE_PROJECT_DIR} or as the plain repository-relative path Claude Code
 * resolves the same way — must point at a script that exists there, catching a hook
 * renamed on disk but not in settings;
 * {@code reportUnreferencedScripts} also reports a script no hook references. The
 * {@code hooksDir} parameter must be configured, but an absent directory is a pass
 * because hooks are optional; a {@code settingsFile} that is configured and absent
 * fails outright, because that is a build-setup mistake. All problems found are
 * reported together.
 */
@Named("hooksFormat")
public class HooksFormatRule extends ClaudeCodeEnforcerRule {

	private static final String SHEBANG = "#!";

	/** The {@code .claude/hooks} directory to scan. Injected from the rule configuration. */
	private File hooksDir;

	/** Optional {@code .claude/settings.json} used to cross-check hook wiring. */
	private File settingsFile;

	/** Base directory that {@code $CLAUDE_PROJECT_DIR} resolves to. Defaults to the settings file's grandparent. */
	private File projectDir;

	/** Optional whitelist of file extensions a hook script may use, e.g. {@code sh}, {@code py}. */
	private List<String> allowedExtensions;

	/** When true (default), each hook script must start with a {@code #!} shebang line. */
	private boolean requireShebang = true;

	/** When true (default), each hook script must carry the executable bit. */
	private boolean requireExecutable = true;

	/** When true, a script in the directory referenced by no settings hook is reported. */
	private boolean reportUnreferencedScripts;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(hooksDir, "hooksDir");
		List<String> violations = new ArrayList<>();
		List<File> scripts = scriptFiles();
		for (File script : scripts) {
			collectScriptViolations(script, violations);
		}
		collectWiringViolations(scripts, violations);
		report("Hook scripts are not well formed:", violations);
	}

	/**
	 * The regular files directly under the hooks directory, sorted.
	 * {@link File#listFiles} yields entries in an unspecified, filesystem-dependent
	 * order, which would let this rule report the same violations in a different
	 * order run-to-run and so churn both the HTML report and a recorded baseline.
	 */
	private List<File> scriptFiles() {
		File[] files = hooksDir.listFiles(File::isFile);
		if (files == null) {
			return List.of();
		}
		Arrays.sort(files);
		return List.of(files);
	}

	/**
	 * A file that cannot be decoded as text is reported rather than read, because
	 * the hooks directory holds whatever a repository put there and an undecodable
	 * file must not abort the build before the remaining scripts are checked.
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
	 * The shebang is read from the raw text, byte-order mark and all. The kernel
	 * reads bytes, so a mark in front of the {@code #!} makes the script unrunnable
	 * — and reading the stripped text reported exactly that script, the one this
	 * rule exists to catch before it is committed, as well formed. A script with no
	 * shebang at all is still reported as the missing shebang it is, so the message
	 * names the problem the author has rather than the one behind it.
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
	 * A configured settings file that is not there is a build-setup mistake, so it
	 * always fails, whatever the severity: reporting it as a violation let
	 * {@code severity=warn} turn the whole wiring cross-check off silently, which is
	 * the one outcome a rule must not have.
	 * <p>
	 * A file that is there but cannot be decoded is reported instead of thrown. This
	 * rule reads settings.json as a second opinion on the scripts, and the rules that
	 * own that file — {@code settingsJsonValid} and the two beside it — fail on it in
	 * their own right, so an undecodable one is never left unreported by this one
	 * declining to abort the build over it.
	 */
	private void collectWiringViolations(List<File> scripts, List<String> violations) throws EnforcerRuleException {
		if (settingsFile == null) {
			return;
		}
		requireExists(settingsFile, "settings.json");
		Optional<String> content = MarkdownText.readIfText(settingsFile);
		if (content.isEmpty()) {
			violations.add("settings.json cannot be read as text: " + settingsFile);
			return;
		}
		JsonNode settings = JsonNodes.parseObject(content.get(), "settings.json", violations);
		if (settings == null) {
			return;
		}
		Set<Path> referenced = collectReferencedScripts(settings, violations);
		collectUnreferencedScripts(scripts, referenced, violations);
	}

	private Set<Path> collectReferencedScripts(JsonNode settings, List<String> violations) {
		Set<Path> referenced = new LinkedHashSet<>();
		for (String command : HookCommands.from(settings)) {
			addReferencedScript(command, referenced, violations);
		}
		return referenced;
	}

	private void addReferencedScript(String command, Set<Path> referenced, List<String> violations) {
		for (Path script : scriptsInHooksDir(command)) {
			referenced.add(script);
			collectMissingScriptViolation(script, violations);
		}
	}

	private void collectMissingScriptViolation(Path script, List<String> violations) {
		if (!script.toFile().exists()) {
			violations.add("settings.json references a missing hook script: " + script);
		}
	}

	private void collectUnreferencedScripts(List<File> scripts, Set<Path> referenced, List<String> violations) {
		if (!reportUnreferencedScripts) {
			return;
		}
		for (File script : scripts) {
			if (!referenced.contains(canonical(script.toPath()))) {
				violations.add("hook script is not referenced by any settings.json hook: " + script);
			}
		}
	}

	/**
	 * The absolute paths of the project-local scripts a command runs that land inside
	 * the hooks directory. A path outside it belongs to another rule's concern, so it
	 * is dropped rather than reported here.
	 */
	private List<Path> scriptsInHooksDir(String command) {
		Path hooks = canonical(hooksDir.toPath());
		return new ClaudeProjectDir(projectDir, settingsFile).scriptsIn(command).stream()
				.map(expanded -> canonical(new File(expanded).toPath()))
				.filter(resolved -> resolved.startsWith(hooks))
				.toList();
	}

	/**
	 * The real, symlink-resolved path of {@code path}. Symlinks are resolved on the
	 * portion that exists on disk and the remaining names are appended, so a hook
	 * script that is a symlink pointing outside the hooks directory no longer
	 * satisfies the containment check by its lexical location alone, while a missing
	 * script is still resolved beneath the real hooks directory so it is reported.
	 */
	private Path canonical(Path path) {
		Path absolute = path.toAbsolutePath().normalize();
		Path existing = absolute;
		while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
			existing = existing.getParent();
		}
		if (existing == null) {
			return absolute;
		}
		return realPath(existing).resolve(existing.relativize(absolute));
	}

	private Path realPath(Path path) {
		try {
			return path.toRealPath();
		} catch (IOException e) {
			return path.toAbsolutePath().normalize();
		}
	}

	void setHooksDir(File hooksDir) {
		this.hooksDir = hooksDir;
	}

	void setSettingsFile(File settingsFile) {
		this.settingsFile = settingsFile;
	}

	void setProjectDir(File projectDir) {
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

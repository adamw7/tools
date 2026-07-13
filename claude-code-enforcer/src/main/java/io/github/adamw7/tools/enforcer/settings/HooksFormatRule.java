package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.rule.JsonNodes;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that fails the build when a hook script under {@code .claude/hooks}
 * is not a well-formed executable, or when {@code .claude/settings.json} wires a
 * command hook to a script that should live in that directory but does not.
 * <p>
 * Where {@link HookCommandsValidRule} validates the JSON shape of the
 * {@code hooks} section, this rule validates the scripts themselves: every
 * regular file directly under {@code hooksDir} must be non-empty, start with a
 * {@code #!} shebang line, and carry the executable bit, so a hook that Claude
 * Code would try to run cannot be committed broken. Each of the script checks can
 * be switched off ({@code requireShebang}, {@code requireExecutable}), and an
 * optional {@code allowedExtensions} whitelist rejects a stray file such as a
 * {@code .txt} note left in the hooks directory.
 * <p>
 * When {@code settingsFile} is configured, the rule cross-checks the wiring: any
 * command hook whose command resolves a {@code $CLAUDE_PROJECT_DIR} path into the
 * hooks directory must point at a script that exists there, catching a hook
 * renamed on disk but not in settings. With {@code reportUnreferencedScripts} a
 * script in the directory that no hook references is reported too.
 * <p>
 * The {@code hooksDir} parameter must be configured, but an absent directory is a
 * pass because hooks are optional in Claude Code. All problems found are reported
 * together.
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

	private List<File> scriptFiles() {
		File[] files = hooksDir.listFiles(File::isFile);
		return files != null ? List.of(files) : List.of();
	}

	private void collectScriptViolations(File script, List<String> violations) {
		String content = MarkdownText.read(script, "hook script");
		if (content.isBlank()) {
			violations.add("hook script is empty: " + script);
			return;
		}
		collectShebangViolation(script, content, violations);
		collectExecutableViolation(script, violations);
		collectExtensionViolation(script, violations);
	}

	private void collectShebangViolation(File script, String content, List<String> violations) {
		if (requireShebang && !content.startsWith(SHEBANG)) {
			violations.add("hook script must start with a '#!' shebang line: " + script);
		}
	}

	private void collectExecutableViolation(File script, List<String> violations) {
		if (requireExecutable && !script.canExecute()) {
			violations.add("hook script is not executable: " + script);
		}
	}

	private void collectExtensionViolation(File script, List<String> violations) {
		if (allowedExtensions != null && !allowedExtensions.contains(extensionOf(script))) {
			violations.add("hook script has a disallowed extension: " + script);
		}
	}

	private String extensionOf(File script) {
		String name = script.getName();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1);
	}

	private void collectWiringViolations(List<File> scripts, List<String> violations) {
		if (settingsFile == null) {
			return;
		}
		if (!settingsFile.isFile()) {
			violations.add("settings.json does not exist at " + settingsFile);
			return;
		}
		JsonNode settings = parseSettings(settingsFile, violations);
		if (settings == null) {
			return;
		}
		Set<Path> referenced = collectReferencedScripts(settings, violations);
		collectUnreferencedScripts(scripts, referenced, violations);
	}

	private JsonNode parseSettings(File file, List<String> violations) {
		return JsonNodes.parseObject(MarkdownText.read(file, "settings.json"), "settings.json", violations);
	}

	private Set<Path> collectReferencedScripts(JsonNode settings, List<String> violations) {
		Set<Path> referenced = new LinkedHashSet<>();
		for (String command : HookCommands.from(settings)) {
			addReferencedScript(command, referenced, violations);
		}
		return referenced;
	}

	private void addReferencedScript(String command, Set<Path> referenced, List<String> violations) {
		Path script = scriptInHooksDir(command);
		if (script == null) {
			return;
		}
		referenced.add(script);
		if (!script.toFile().exists()) {
			violations.add("settings.json references a missing hook script: " + script);
		}
	}

	private void collectUnreferencedScripts(List<File> scripts, Set<Path> referenced, List<String> violations) {
		if (!reportUnreferencedScripts) {
			return;
		}
		for (File script : scripts) {
			addUnreferencedViolation(script, referenced, violations);
		}
	}

	private void addUnreferencedViolation(File script, Set<Path> referenced, List<String> violations) {
		if (!referenced.contains(canonical(script.toPath()))) {
			violations.add("hook script is not referenced by any settings.json hook: " + script);
		}
	}

	/** The absolute path a command's {@code $CLAUDE_PROJECT_DIR} token resolves to when it lands in the hooks directory, else null. */
	private Path scriptInHooksDir(String command) {
		Path hooks = canonical(hooksDir.toPath());
		ClaudeProjectDir projectDirs = new ClaudeProjectDir(projectDir, settingsFile);
		for (String token : command.split("\\s+")) {
			Path resolved = resolveInHooks(projectDirs, token, hooks);
			if (resolved != null) {
				return resolved;
			}
		}
		return null;
	}

	private Path resolveInHooks(ClaudeProjectDir projectDirs, String token, Path hooks) {
		String expanded = projectDirs.expand(token);
		if (expanded == null) {
			return null;
		}
		Path resolved = canonical(new File(expanded).toPath());
		return resolved.startsWith(hooks) ? resolved : null;
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

	@Override
	public String toString() {
		return String.format("HooksFormatRule[hooksDir=%s]", hooksDir);
	}
}

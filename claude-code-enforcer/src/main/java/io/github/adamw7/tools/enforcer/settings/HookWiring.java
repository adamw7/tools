package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.enforcer.rule.JsonNodes;
import io.github.adamw7.tools.enforcer.rule.ProjectFiles;
import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * The cross-check between the scripts in a hooks directory and the command hooks
 * {@code settings.json} wires to them: a hook that names a script the directory
 * does not hold, and — when asked for — a script the directory holds that no hook
 * names.
 * <p>
 * It answers a different question from {@link HooksFormatRule} with different
 * inputs: it reads settings.json, walks its hooks through {@link HookCommands}, and
 * resolves what they name through {@link ClaudeProjectDir}. Keeping it beside the
 * rule leaves that rule to the scripts themselves, and puts the path
 * canonicalisation the containment check needs where nothing else reads past it.
 * A settings.json that cannot be decoded is reported rather than thrown, the rules
 * that own that file failing on it in their own right.
 */
final class HookWiring {

	private final File hooksDir;
	private final File settingsFile;
	private final File projectDir;
	private final boolean reportUnreferencedScripts;

	HookWiring(File hooksDir, File settingsFile, File projectDir, boolean reportUnreferencedScripts) {
		this.hooksDir = hooksDir;
		this.settingsFile = settingsFile;
		this.projectDir = projectDir;
		this.reportUnreferencedScripts = reportUnreferencedScripts;
	}

	/** Collects everything the wiring between {@code scripts} and the settings file gets wrong. */
	void collectViolations(List<File> scripts, List<String> violations) {
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
	 * The real, symlink-resolved path of {@code path}: resolved on the portion that
	 * exists and the remaining names appended, so a script symlinked outside the
	 * hooks directory no longer satisfies containment by its lexical location, while
	 * a missing one still resolves beneath the real directory and is reported.
	 */
	private Path canonical(Path path) {
		Path absolute = ProjectFiles.normalized(path);
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
			return ProjectFiles.normalized(path);
		}
	}
}

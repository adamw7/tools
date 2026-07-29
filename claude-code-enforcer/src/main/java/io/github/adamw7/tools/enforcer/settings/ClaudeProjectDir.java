package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Resolves the base directory that a hook command's {@code $CLAUDE_PROJECT_DIR}
 * variable expands to, and performs that expansion on the tokens of a command.
 * Both hook rules share this so the two accepted spellings of the variable and
 * the "grandparent of the settings file" fallback live in exactly one place.
 */
final class ClaudeProjectDir {

	static final String BRACED = "${CLAUDE_PROJECT_DIR}";
	static final String PLAIN = "$CLAUDE_PROJECT_DIR";

	private final File override;
	private final File settingsFile;

	ClaudeProjectDir(File override, File settingsFile) {
		this.override = override;
		this.settingsFile = settingsFile;
	}

	/**
	 * The paths every {@code $CLAUDE_PROJECT_DIR}-rooted token of {@code command}
	 * resolves to. A command chains more than one script often enough — an
	 * {@code &&} between two hooks — that stopping at the first reference would
	 * leave the rest unchecked.
	 */
	List<String> expandAll(String command) {
		return CommandTokens.of(command).stream().map(this::expand).filter(Objects::nonNull).toList();
	}

	/** The path {@code token} resolves to when it references the project dir, else null. */
	String expand(String token) {
		String bare = withoutQuotes(token);
		if (!bare.contains(BRACED) && !bare.contains(PLAIN)) {
			return null;
		}
		String root = resolve().getPath();
		return bare.replace(BRACED, root).replace(PLAIN, root);
	}

	/**
	 * The token without shell quote characters, so a quoted
	 * {@code "$CLAUDE_PROJECT_DIR/hook.sh"} resolves to the same on-disk path as
	 * its unquoted spelling — the shell removes the quotes after expansion, and a
	 * hook path never legitimately contains one.
	 */
	private String withoutQuotes(String token) {
		return token.replace("\"", "").replace("'", "");
	}

	/** The configured override, else the settings file's grandparent, else the current directory. */
	File resolve() {
		if (override != null) {
			return override;
		}
		File claudeDir = settingsFile.getAbsoluteFile().getParentFile();
		File root = claudeDir != null ? claudeDir.getParentFile() : null;
		return root != null ? root : new File(".");
	}
}

package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the project-local scripts a hook command runs, against the base
 * directory its {@code $CLAUDE_PROJECT_DIR} variable expands to. Both hook rules
 * share this so the two accepted spellings of the variable, the "grandparent of the
 * settings file" fallback, and what counts as a project-local script live in
 * exactly one place.
 * <p>
 * The unbraced spelling ends where the variable name does, as a shell reads it, so
 * {@code $CLAUDE_PROJECT_DIR_BACKUP} is a different variable and not this one with
 * a suffix. Expanding it as this one would invent a path no hook ever named and
 * report it as a missing script.
 */
final class ClaudeProjectDir {

	static final String BRACED = "${CLAUDE_PROJECT_DIR}";
	static final String PLAIN = "$CLAUDE_PROJECT_DIR";

	/** The unbraced spelling, only where the name is not continued by a further identifier character. */
	private static final Pattern PLAIN_REFERENCE = Pattern.compile(Pattern.quote(PLAIN) + "(?![A-Za-z0-9_])");

	private final File override;
	private final File settingsFile;

	ClaudeProjectDir(File override, File settingsFile) {
		this.override = override;
		this.settingsFile = settingsFile;
	}

	/**
	 * The project-local scripts {@code command} runs, whichever way each is spelled:
	 * rooted at {@code $CLAUDE_PROJECT_DIR}, or as the plain repository-relative path
	 * Claude Code resolves the same way.
	 * <p>
	 * Both spellings name the same file — Claude Code runs a hook from the project
	 * directory — so both are read from the same tokens, the scripts of the command
	 * as {@link CommandTokens#scriptCandidatesOf} picks them out. Expanding
	 * <em>every</em> token mentioning the variable made the two disagree and failed a
	 * build over an argument the hook was about to create: a
	 * {@code mkdir -p "$CLAUDE_PROJECT_DIR/target/logs"} was reported as a missing
	 * script while the identical {@code mkdir -p target/logs} passed.
	 * <p>
	 * A command naming one script both ways yields it once. The separators are
	 * normalised first, since an expansion keeps the ones the command was written
	 * with while a relative path is resolved as a {@link File}, and the same script
	 * would otherwise be reported as two.
	 */
	List<String> scriptsIn(String command) {
		return CommandTokens.scriptCandidatesOf(command).stream()
				.map(this::resolveScript)
				.flatMap(Optional::stream)
				.map(path -> new File(path).getPath())
				.distinct()
				.toList();
	}

	/** The path a script token resolves to, or empty when it names no file this rule can see. */
	private Optional<String> resolveScript(String token) {
		String bare = withoutQuotes(token);
		if (references(bare)) {
			return Optional.of(expanded(bare));
		}
		return isRelativeScript(bare) ? Optional.of(new File(resolve(), bare).getPath()) : Optional.empty();
	}

	/**
	 * Whether a token names a repository-relative script rather than a tool the shell
	 * finds for itself: {@code ./run.sh} does, while {@code bash} and {@code npx} name
	 * no path and are looked up on the {@code PATH}. An absolute path, a home-relative
	 * one and one carrying a shell expansion each name a file outside the repository's
	 * control, so none is a script this rule may require.
	 */
	private static boolean isRelativeScript(String token) {
		return token.indexOf('/') > 0 && token.indexOf('$') < 0 && !token.startsWith("~")
				&& !token.startsWith("-");
	}

	/** The path a project-dir reference expands to, with both spellings of the variable resolved. */
	private String expanded(String bare) {
		String root = resolve().getPath();
		return PLAIN_REFERENCE.matcher(bare.replace(BRACED, root)).replaceAll(Matcher.quoteReplacement(root));
	}

	private boolean references(String bare) {
		return bare.contains(BRACED) || PLAIN_REFERENCE.matcher(bare).find();
	}

	/**
	 * The token without shell quote characters, so a quoted path resolves to the same
	 * on-disk path as its unquoted spelling: the shell removes the quotes after
	 * expansion, and a hook path never legitimately contains one.
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

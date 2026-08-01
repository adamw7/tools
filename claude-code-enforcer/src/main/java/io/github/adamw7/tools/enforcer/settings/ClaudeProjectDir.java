package io.github.adamw7.tools.enforcer.settings;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
	 * The project-local scripts {@code command} runs: every token rooted at
	 * {@code $CLAUDE_PROJECT_DIR}, plus the program of each chained command written
	 * as a plain repository-relative path.
	 * <p>
	 * Both spellings name the same file — Claude Code runs a hook from the project
	 * directory, so {@code .claude/hooks/session-start.sh} resolves exactly where
	 * {@code $CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh} does — and a
	 * settings.json is as likely to be written either way. Reading only the variable
	 * left the relative spelling unchecked: a hook pointing at a script that had been
	 * renamed passed, which is the very failure these rules exist to catch, and
	 * {@code hooksFormat} reported the script it really did reference as referenced
	 * by nothing.
	 * <p>
	 * A command that names one script both ways yields it once. The separators are
	 * normalised before the comparison because the two spellings are assembled
	 * differently — an expansion keeps the separators the command was written with,
	 * a relative program is resolved as a {@link File} — and on a platform where
	 * those disagree the same script would otherwise be reported as two.
	 */
	List<String> scriptsIn(String command) {
		return Stream.concat(expandAll(command).stream(), relativePrograms(command).stream())
				.map(path -> new File(path).getPath())
				.distinct()
				.toList();
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

	/**
	 * Only a program is read as a relative script, never an argument — see
	 * {@link CommandTokens#programsOf} — so a hook passing a path to the script it
	 * runs does not have that path required to exist too.
	 */
	private List<String> relativePrograms(String command) {
		return CommandTokens.programsOf(command).stream()
				.map(this::withoutQuotes)
				.filter(ClaudeProjectDir::isRelativeScript)
				.map(program -> new File(resolve(), program).getPath())
				.toList();
	}

	/**
	 * Whether a program names a repository-relative script rather than a tool the
	 * shell finds for itself: {@code .claude/hooks/session-start.sh} and
	 * {@code ./run.sh} do, while {@code bash}, {@code python3}, and {@code npx} name
	 * no path at all and are looked up on the {@code PATH}. An absolute path, a
	 * home-relative one, and one carrying a shell expansion all name a file outside
	 * the repository's control — or one only a shell can resolve — so none of them is
	 * a script this rule may require.
	 */
	private static boolean isRelativeScript(String program) {
		return program.indexOf('/') > 0 && program.indexOf('$') < 0 && !program.startsWith("~")
				&& !program.startsWith("-");
	}

	/** The path {@code token} resolves to when it references the project dir, else null. */
	String expand(String token) {
		String bare = withoutQuotes(token);
		if (!references(bare)) {
			return null;
		}
		String root = resolve().getPath();
		return PLAIN_REFERENCE.matcher(bare.replace(BRACED, root)).replaceAll(Matcher.quoteReplacement(root));
	}

	private boolean references(String bare) {
		return bare.contains(BRACED) || PLAIN_REFERENCE.matcher(bare).find();
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

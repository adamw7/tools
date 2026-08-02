package io.github.adamw7.tools.enforcer.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Splits a hook command into the tokens a shell would see. Both hook rules need
 * this to find the script paths inside a command, so the splitting lives here
 * rather than in each of them.
 * <p>
 * Splitting is on whitespace and on the shell separators {@code ;}, {@code &},
 * {@code |}, a newline, and the {@code (}/{@code )} of a subshell, all
 * <em>outside</em> quotes. Whitespace alone is not enough: a command
 * chaining two hooks as {@code hook-a.sh; hook-b.sh} leaves the semicolon glued to
 * the first path, and the rules would then report a script that is really there as
 * missing. Quoting is honoured because a hook script path containing a space is
 * legitimate and is quoted for exactly that reason; a plain split would tear
 * {@code "$CLAUDE_PROJECT_DIR/my hook.sh"} into two halves. The quote characters are
 * kept on the token, since {@link ClaudeProjectDir} strips them as part of expanding
 * it.
 * <p>
 * {@link #scriptCandidatesOf} answers the narrower question of which tokens name a
 * file the shell would execute or read as a script, which is what a rule resolving
 * a path on disk has to go on: a program is one, as is the script an interpreter is
 * handed, but never an argument that merely happens to look like a path.
 */
final class CommandTokens {

	private static final char NONE = 0;
	private static final char DOUBLE_QUOTE = '"';
	private static final char SINGLE_QUOTE = '\'';
	private static final char PATH_SEPARATOR = '/';
	private static final String OPTION_PREFIX = "-";
	private static final String LONG_OPTION_PREFIX = "--";
	private static final char INLINE_SCRIPT_FLAG = 'c';

	/**
	 * The programs that run a script named by their first non-option argument rather
	 * than being that script themselves. A hook wired as {@code bash <script>} names a
	 * file that must exist just as plainly as one wired as {@code <script>}, and
	 * reading only the program would leave it unchecked.
	 */
	private static final List<String> INTERPRETERS = List.of(
			"bash", "sh", "zsh", "dash", "ksh", "python", "python3", "node", "ruby", "perl");

	/**
	 * The shell operators that end a token just as whitespace does, and that end one
	 * command and begin the next. A newline is one of them: a hook command written
	 * across several lines runs one program per line, and reading only the first
	 * would leave the rest of its scripts unresolved — and, with
	 * {@code reportUnreferencedScripts}, report a script the hook really does run as
	 * referenced by nothing. The parentheses of a subshell are operators for the
	 * same reason, and because gluing one to the path inside it invents a program
	 * named {@code (script.sh)} that no file can match.
	 */
	private static final String OPERATORS = ";&|\n\r()";

	/**
	 * A {@code VAR=value} prefix, which sets a variable for the command that follows
	 * rather than naming a program of its own. A shell skips over every such
	 * assignment to find what it runs, so reading the first token blindly would take
	 * {@code FOO=bar} for the program and leave the real script unresolved.
	 */
	private static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=.*");

	private CommandTokens() {
	}

	/** The command's tokens, in order, with empty ones dropped. */
	static List<String> of(String command) {
		return split(command, CommandTokens::isTokenSeparator);
	}

	/**
	 * The tokens of {@code command} that name a script file: the program each
	 * operator-separated segment runs, plus the script an interpreter among them is
	 * handed, in order.
	 * <p>
	 * A hook chaining two scripts names two of them, so every segment is read. What is
	 * deliberately left out is an ordinary argument, however much it looks like a path:
	 * a hook invoked as {@code .claude/hooks/build.sh --out target/log.txt} names one
	 * script and one output file, and requiring the second to exist would fail a build
	 * over a file the hook is about to write. The same goes for the directory of a
	 * {@code mkdir -p}.
	 */
	static List<String> scriptCandidatesOf(String command) {
		return split(command, CommandTokens::isOperator).stream()
				.map(CommandTokens::of)
				.flatMap(CommandTokens::candidatesIn)
				.toList();
	}

	/** The program of one segment, and the script it hands on when that program is an interpreter. */
	private static Stream<String> candidatesIn(List<String> tokens) {
		List<String> words = tokens.stream().dropWhile(token -> ASSIGNMENT.matcher(token).matches()).toList();
		if (words.isEmpty()) {
			return Stream.empty();
		}
		String program = words.getFirst();
		return Stream.concat(Stream.of(program), interpretedScript(program, words.subList(1, words.size())).stream());
	}

	/**
	 * The script an interpreter is handed: its first non-option argument. A segment
	 * carrying the {@code -c} flag names no file at all — the argument is the script's
	 * text — so it yields nothing rather than a path that was never meant to be one.
	 */
	private static Optional<String> interpretedScript(String program, List<String> arguments) {
		if (!INTERPRETERS.contains(commandName(program)) || arguments.stream().anyMatch(CommandTokens::isInlineScript)) {
			return Optional.empty();
		}
		return arguments.stream().filter(argument -> !argument.startsWith(OPTION_PREFIX)).findFirst();
	}

	/**
	 * True for the {@code -c} flag, alone or inside a cluster of short ones. A shell
	 * reads {@code -ec} as {@code -e -c} just as it reads {@code -c}, and missing the
	 * cluster would take the inline script's text for a path on disk.
	 */
	private static boolean isInlineScript(String argument) {
		return argument.startsWith(OPTION_PREFIX) && !argument.startsWith(LONG_OPTION_PREFIX)
				&& argument.indexOf(INLINE_SCRIPT_FLAG) > 0;
	}

	/** The program's own name, so an interpreter is recognised however it was spelled on disk. */
	private static String commandName(String program) {
		return program.substring(program.lastIndexOf(PATH_SEPARATOR) + 1);
	}

	/** Splits on every {@code separator} character outside quotes, dropping the empty pieces. */
	private static List<String> split(String command, IntPredicate separator) {
		List<String> pieces = new ArrayList<>();
		StringBuilder piece = new StringBuilder();
		char quote = NONE;
		for (int i = 0; i < command.length(); i++) {
			quote = append(command.charAt(i), quote, piece, pieces, separator);
		}
		addPiece(piece, pieces);
		return pieces;
	}

	/**
	 * Appends one character to the piece being built and returns the quote
	 * character still open afterwards, so the caller stays a plain loop.
	 */
	private static char append(char character, char quote, StringBuilder piece, List<String> pieces,
			IntPredicate separator) {
		if (quote != NONE) {
			piece.append(character);
			return character == quote ? NONE : quote;
		}
		if (character == DOUBLE_QUOTE || character == SINGLE_QUOTE) {
			piece.append(character);
			return character;
		}
		if (separator.test(character)) {
			addPiece(piece, pieces);
			return NONE;
		}
		piece.append(character);
		return NONE;
	}

	private static boolean isTokenSeparator(int character) {
		return Character.isWhitespace(character) || isOperator(character);
	}

	private static boolean isOperator(int character) {
		return OPERATORS.indexOf(character) >= 0;
	}

	private static void addPiece(StringBuilder piece, List<String> pieces) {
		if (piece.length() > 0) {
			pieces.add(piece.toString());
			piece.setLength(0);
		}
	}
}

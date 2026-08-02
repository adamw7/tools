package io.github.adamw7.tools.enforcer.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

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
 * {@link #programsOf} answers the narrower question of which tokens a shell would
 * actually <em>run</em>, which is what a rule reading a bare relative path as a
 * script has to go on: only a program can be one, never an argument that happens to
 * look like a path.
 */
final class CommandTokens {

	private static final char NONE = 0;
	private static final char DOUBLE_QUOTE = '"';
	private static final char SINGLE_QUOTE = '\'';

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
	 * The program each command in the string runs: the first token of every
	 * operator-separated segment that is not a {@code VAR=value} assignment, in
	 * order.
	 * <p>
	 * A hook chaining two scripts runs two programs, and only a program is a script
	 * the rules may resolve as a bare relative path. An argument is not, however much
	 * it looks like one: a hook invoked as {@code .claude/hooks/build.sh --out
	 * target/log.txt} names one script and one output file, and requiring the second
	 * to exist would fail a build over a file the hook is about to write.
	 */
	static List<String> programsOf(String command) {
		return split(command, CommandTokens::isOperator).stream()
				.map(CommandTokens::of)
				.map(CommandTokens::programOf)
				.flatMap(Optional::stream)
				.toList();
	}

	/** The token a shell would run: the first that is not a leading {@code VAR=value} assignment. */
	private static Optional<String> programOf(List<String> tokens) {
		return tokens.stream().dropWhile(token -> ASSIGNMENT.matcher(token).matches()).findFirst();
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

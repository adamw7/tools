package io.github.adamw7.tools.enforcer.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
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
	private static final char SET_OPTION_PREFIX = '+';

	/**
	 * The programs that run a script named by one of their arguments rather than
	 * being that script themselves, each described by how its own options are
	 * written. A hook wired as {@code bash <script>} names a file that must exist
	 * just as plainly as one wired as {@code <script>}, and reading only the program
	 * would leave it unchecked.
	 * <p>
	 * The descriptions differ per program because the same letter means different
	 * things to different ones: a shell's {@code -e} stops on error while node's,
	 * perl's and ruby's carries the script's own text, and python's {@code -m} takes
	 * a module name where a shell's takes nothing at all.
	 */
	private static final Map<String, Interpreter> INTERPRETERS = interpreters();

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

	/**
	 * The shell reserved words, which structure a command rather than name a program.
	 * A hook that runs its script only under a condition writes
	 * {@code if [ -n "$CI" ]; then .claude/hooks/session-start.sh; fi}, and reading
	 * the first word of each segment blindly took {@code then} for the program — so
	 * the script went unresolved, and {@code reportUnreferencedScripts} reported a
	 * script the hook really does run as referenced by nothing. A shell skips over
	 * these the same way it skips a {@code VAR=value} prefix, and so does this: the
	 * word after them is what runs.
	 */
	private static final Set<String> RESERVED_WORDS = Set.of(
			"if", "then", "elif", "else", "fi",
			"for", "while", "until", "do", "done",
			"case", "in", "esac",
			"{", "}", "!");

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
		List<String> words = tokens.stream().dropWhile(CommandTokens::isPrefix).toList();
		if (words.isEmpty()) {
			return Stream.empty();
		}
		String program = words.getFirst();
		return Stream.concat(Stream.of(program), interpretedScript(program, words.subList(1, words.size())).stream());
	}

	/** True for a word that precedes the program rather than being it: an assignment or a reserved word. */
	private static boolean isPrefix(String token) {
		return ASSIGNMENT.matcher(token).matches() || RESERVED_WORDS.contains(token);
	}

	/** The script an interpreter is handed, or nothing when the program is not one. */
	private static Optional<String> interpretedScript(String program, List<String> arguments) {
		Interpreter interpreter = INTERPRETERS.get(commandName(program));
		return interpreter == null ? Optional.empty() : interpreter.scriptIn(arguments);
	}

	/** The program's own name, so an interpreter is recognised however it was spelled on disk. */
	private static String commandName(String program) {
		return program.substring(program.lastIndexOf(PATH_SEPARATOR) + 1);
	}

	/**
	 * How one interpreter writes its options, which is all a reader needs to find the
	 * script among its arguments: the short flags whose argument is the script's own
	 * text rather than a path, the long options that say the same, and the flags and
	 * options that consume the word after them.
	 * <p>
	 * The last of these is what a naive "first non-option argument" misses. A hook
	 * wired as {@code bash -euo pipefail .claude/hooks/session-start.sh} hands the
	 * word {@code pipefail} to the {@code -o} ending the cluster, so reading it as the
	 * script both invented a file no hook ever named and hid the real one behind it —
	 * and with {@code reportUnreferencedScripts}, reported a script the hook really
	 * does run as referenced by nothing.
	 *
	 * @param inlineFlags   the short flags carrying the script's text, e.g. a
	 *                      shell's {@code c} or node's {@code e} and {@code p}
	 * @param valueFlags    the short flags consuming the word after them, e.g. a
	 *                      shell's {@code o}
	 * @param inlineOptions the long options carrying the script's text
	 * @param valueOptions  the long options consuming the word after them
	 */
	private record Interpreter(String inlineFlags, String valueFlags, Set<String> inlineOptions,
			Set<String> valueOptions) {

		/**
		 * The script this interpreter is handed. A segment carrying an inline-script
		 * option names no file at all, so it yields nothing rather than a path that was
		 * never meant to be one.
		 */
		Optional<String> scriptIn(List<String> arguments) {
			if (arguments.stream().anyMatch(this::isInlineScript)) {
				return Optional.empty();
			}
			return IntStream.range(0, arguments.size())
					.filter(index -> isOperand(arguments, index))
					.mapToObj(arguments::get)
					.findFirst();
		}

		/** True for the first word that is neither an option nor the value of the option before it. */
		private boolean isOperand(List<String> arguments, int index) {
			return !isOption(arguments.get(index))
					&& (index == 0 || !consumesValue(arguments.get(index - 1)));
		}

		/**
		 * An option is written with a leading dash, or is one of the {@code +o}
		 * spellings a shell also takes — which a leading dash alone would leave looking
		 * like the script.
		 */
		private boolean isOption(String argument) {
			return argument.startsWith(OPTION_PREFIX) || consumesValue(argument);
		}

		private boolean isInlineScript(String argument) {
			return inlineOptions.contains(argument) || carriesFlag(argument, inlineFlags);
		}

		private boolean consumesValue(String argument) {
			return valueOptions.contains(argument) || endsWithFlag(argument, valueFlags);
		}

		/**
		 * True when a cluster of short options carries one of {@code flags} anywhere in
		 * it. A shell reads {@code -ec} as {@code -e -c} just as it reads {@code -c},
		 * and missing the cluster would take the inline script's text for a path on
		 * disk.
		 */
		private boolean carriesFlag(String argument, String flags) {
			return isShortCluster(argument) && argument.chars().skip(1).anyMatch(flag -> flags.indexOf(flag) >= 0);
		}

		/**
		 * True when a cluster of short options <em>ends</em> in one of {@code flags}.
		 * Only the last flag of a cluster can take the word after it, which is why
		 * {@code -euo} swallows a value and {@code -oe} does not.
		 */
		private boolean endsWithFlag(String argument, String flags) {
			return isShortCluster(argument) && flags.indexOf(argument.charAt(argument.length() - 1)) >= 0;
		}

		/** A run of short options, written with the leading {@code -} or the {@code +} that unsets them. */
		private static boolean isShortCluster(String argument) {
			return argument.length() > 1 && !argument.startsWith(LONG_OPTION_PREFIX)
					&& (argument.startsWith(OPTION_PREFIX) || argument.charAt(0) == SET_OPTION_PREFIX);
		}
	}

	/**
	 * The interpreters this reader knows, each named by every spelling a hook invokes
	 * it with. Only the options that decide where the script is are listed: one that
	 * neither carries the script nor takes a value needs no entry, because a leading
	 * dash is already enough to skip it.
	 */
	private static Map<String, Interpreter> interpreters() {
		Interpreter shell = new Interpreter("c", "o", Set.of(), Set.of("--rcfile", "--init-file"));
		Interpreter python = new Interpreter("c", "mWXQ", Set.of(), Set.of("--check-hash-based-pycs"));
		Interpreter node = new Interpreter("ep", "r", Set.of("--eval", "--print"),
				Set.of("--require", "--import", "--loader", "--experimental-loader", "--conditions"));
		Interpreter ruby = new Interpreter("e", "rICEK", Set.of(), Set.of());
		Interpreter perl = new Interpreter("e", "IMm", Set.of(), Set.of());
		return Map.of("bash", shell, "sh", shell, "zsh", shell, "dash", shell, "ksh", shell,
				"python", python, "python3", python, "node", node, "ruby", ruby, "perl", perl);
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

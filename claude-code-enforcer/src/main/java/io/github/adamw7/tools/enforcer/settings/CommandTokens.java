package io.github.adamw7.tools.enforcer.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a hook command into the tokens a shell would see. Both hook rules need
 * this to find the {@code $CLAUDE_PROJECT_DIR} references inside a command, so
 * the splitting lives here rather than in each of them.
 * <p>
 * Splitting is on whitespace and on the shell separators {@code ;}, {@code &} and
 * {@code |}, all <em>outside</em> quotes. Whitespace alone is not enough: a command
 * chaining two hooks as {@code hook-a.sh; hook-b.sh} leaves the semicolon glued to
 * the first path, and the rules would then report a script that is really there as
 * missing. Quoting is honoured because a hook script path containing a space is
 * legitimate and is quoted for exactly that reason; a plain split would tear
 * {@code "$CLAUDE_PROJECT_DIR/my hook.sh"} into two halves. The quote characters are
 * kept on the token, since {@link ClaudeProjectDir} strips them as part of expanding
 * it.
 */
final class CommandTokens {

	private static final char NONE = 0;
	private static final char DOUBLE_QUOTE = '"';
	private static final char SINGLE_QUOTE = '\'';

	/** The shell operators that end a token just as whitespace does. */
	private static final String SEPARATORS = ";&|";

	private CommandTokens() {
	}

	/** The command's tokens, in order, with empty ones dropped. */
	static List<String> of(String command) {
		List<String> tokens = new ArrayList<>();
		StringBuilder token = new StringBuilder();
		char quote = NONE;
		for (int i = 0; i < command.length(); i++) {
			quote = append(command.charAt(i), quote, token, tokens);
		}
		addToken(token, tokens);
		return tokens;
	}

	/**
	 * Appends one character to the token being built and returns the quote
	 * character still open afterwards, so the caller stays a plain loop.
	 */
	private static char append(char character, char quote, StringBuilder token, List<String> tokens) {
		if (quote != NONE) {
			token.append(character);
			return character == quote ? NONE : quote;
		}
		if (character == DOUBLE_QUOTE || character == SINGLE_QUOTE) {
			token.append(character);
			return character;
		}
		if (isSeparator(character)) {
			addToken(token, tokens);
			return NONE;
		}
		token.append(character);
		return NONE;
	}

	private static boolean isSeparator(char character) {
		return Character.isWhitespace(character) || SEPARATORS.indexOf(character) >= 0;
	}

	private static void addToken(StringBuilder token, List<String> tokens) {
		if (token.length() > 0) {
			tokens.add(token.toString());
			token.setLength(0);
		}
	}
}

package io.github.adamw7.tools.mcp;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.adamw7.tools.secret.Redaction;

/**
 * What a failed tool call is allowed to say, on each of the two channels a failure
 * reaches: the server's log, and the error result the client reads. Every tool of
 * every server funnels through {@link AbstractMcpConfiguration#safeApply}, so this is
 * the one place either can leak.
 *
 * <p>The log keeps the call's arguments, because a failure nobody can reproduce is
 * not much of a log line, but a credential in them is masked: an MCP server is
 * long-lived, and a token written to its log outlives every session that could have
 * used it. The client's message is masked further — the filesystem locations a
 * failure names are the server's, not the caller's, and a denied path answering with
 * where the server keeps its files hands back exactly what the path confinement is
 * there to withhold.
 */
final class FailureMessage {

	/**
	 * An absolute POSIX path. The leading separator must not follow a word character,
	 * a colon or another separator, so the {@code /owner/repo.git} of a URL — whose
	 * host is already the word before it — is left as the URL it is part of rather
	 * than mistaken for a directory. Quotes and whitespace end the path, since a
	 * message quotes the paths it names.
	 */
	private static final Pattern POSIX_PATH = Pattern.compile("(?<![\\w:/])/(?:[^/\\s'\"]+/)+[^/\\s'\",;)\\]]*");

	/** The same for a Windows path, which names its drive and separates with backslashes. */
	private static final Pattern WINDOWS_PATH =
			Pattern.compile("(?<![\\w])[A-Za-z]:\\\\(?:[^\\\\\\s'\"]+\\\\)*[^\\\\\\s'\",;)\\]]*");

	private static final String ELLIPSIS = "...";

	private FailureMessage() {
	}

	/**
	 * @return the call's arguments as the log may carry them
	 */
	static String forLog(Map<String, Object> arguments) {
		return Redaction.of(String.valueOf(arguments));
	}

	/**
	 * @return what the failure may tell the client: its message — or the exception's
	 *         own {@code toString()} when it carries none, so a
	 *         {@link NullPointerException} still names something — with credentials
	 *         and filesystem locations taken out of it
	 */
	static String forClient(RuntimeException e) {
		String message = e.getMessage() == null ? e.toString() : e.getMessage();
		return withoutLocations(Redaction.of(message));
	}

	/**
	 * Keeps the last segment of every absolute path, which is the part the caller
	 * named and the part that says what went wrong, and replaces the directories
	 * leading to it with {@value #ELLIPSIS}.
	 */
	private static String withoutLocations(String message) {
		return abbreviate(abbreviate(message, POSIX_PATH, '/'), WINDOWS_PATH, '\\');
	}

	private static String abbreviate(String message, Pattern path, char separator) {
		return path.matcher(message)
				.replaceAll(match -> Matcher.quoteReplacement(ELLIPSIS + separator + lastSegment(match.group(), separator)));
	}

	private static String lastSegment(String path, char separator) {
		return path.substring(path.lastIndexOf(separator) + 1);
	}
}

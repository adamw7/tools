package io.github.adamw7.tools.adopt.command;

/**
 * A command's captured output, bounded so a talkative child cannot cost the
 * adoption unbounded memory. The beginning and the end are kept and the middle is
 * dropped, because that is where a command says what it was asked to do and how it
 * went: the invocation and its first errors are at the top, the failure that
 * stopped it at the bottom, and the thousands of progress lines between them are
 * what a transcript grows by.
 *
 * <p>Bounding it here rather than at the caller matters because the transcript does
 * not stop at the log: a failing command's whole output becomes the {@code failure}
 * field of the JSON report written to disk and answered to MCP clients, so an
 * unbounded transcript is an unbounded response.
 *
 * <p>A transcript that fits is answered verbatim. Only one that overflowed is cut,
 * and then <em>only at line boundaries</em>: {@link io.github.adamw7.tools.adopt.Redaction}
 * masks a clone URL's credentials by matching the user information after a
 * {@code ://}, so a cut through the middle of one would leave the token with nothing
 * to recognise it by. A region carrying no line feed is dropped whole.
 */
final class BoundedTranscript {

	/** Kept from the start of the output: the command's invocation and its first errors. */
	static final int DEFAULT_HEAD_LIMIT = 64 * 1024;

	/** Kept from the end of the output, at minimum: the failure that stopped the command. */
	static final int DEFAULT_TAIL_LIMIT = 64 * 1024;

	private static final char LINE_FEED = '\n';

	private final int headLimit;
	private final int tailLimit;

	private final StringBuilder head = new StringBuilder();

	/**
	 * The end of the output, held as two windows that are retired whole. Sliding a
	 * buffer of the last {@link #tailLimit} characters would shift it once per
	 * character read; rotating a filled window costs nothing per character and keeps
	 * between {@code tailLimit} and twice that much, which is bounded just the same.
	 */
	private StringBuilder previousWindow = new StringBuilder();
	private StringBuilder currentWindow = new StringBuilder();

	/** Characters retired with a window, and so no longer recoverable. */
	private long dropped;

	BoundedTranscript() {
		this(DEFAULT_HEAD_LIMIT, DEFAULT_TAIL_LIMIT);
	}

	BoundedTranscript(int headLimit, int tailLimit) {
		this.headLimit = requirePositive(headLimit, "headLimit");
		this.tailLimit = requirePositive(tailLimit, "tailLimit");
	}

	/**
	 * Appends the first {@code length} characters of {@code buffer}. Synchronized
	 * because the reader thread appends while {@link #text()} may already be reading:
	 * {@link StreamGobbler} bounds its wait for that thread, so a descendant holding
	 * the pipe open leaves the two genuinely concurrent.
	 */
	synchronized void append(char[] buffer, int length) {
		int intoHead = Math.min(headLimit - head.length(), length);
		head.append(buffer, 0, intoHead);
		appendToTail(buffer, intoHead, length - intoHead);
	}

	/** @return everything captured, or the kept regions and a count of what was dropped */
	synchronized String text() {
		String tail = previousWindow.toString() + currentWindow;
		return dropped == 0 ? head + tail : elided(tail);
	}

	/**
	 * A run at least as long as the whole tail makes both windows redundant — its own
	 * last {@link #tailLimit} characters are the end of the output — so it replaces
	 * them rather than being appended to one. Without that, a caller reading the
	 * stream in one go would land the entire output in a single window, and the
	 * rotation below would never have anything to drop.
	 */
	private void appendToTail(char[] buffer, int offset, int length) {
		if (length >= tailLimit) {
			replaceWindows(buffer, offset, length);
		} else {
			currentWindow.append(buffer, offset, length);
			rotateWhenFull();
		}
	}

	private void replaceWindows(char[] buffer, int offset, int length) {
		dropped += previousWindow.length() + currentWindow.length() + (length - tailLimit);
		previousWindow = new StringBuilder();
		currentWindow = new StringBuilder().append(buffer, offset + length - tailLimit, tailLimit);
	}

	private void rotateWhenFull() {
		if (currentWindow.length() < tailLimit) {
			return;
		}
		dropped += previousWindow.length();
		previousWindow = currentWindow;
		currentWindow = new StringBuilder();
	}

	/**
	 * Both kept regions are cut back to a line boundary before they are joined, so
	 * neither ends nor begins mid-line. The count names every character left out —
	 * the ones dropped as the output arrived, and the partial lines trimmed here.
	 */
	private String elided(String tail) {
		String keptHead = throughLastLine(head.toString());
		String keptTail = fromFirstLine(tail);
		long omitted = dropped + (head.length() - keptHead.length()) + (tail.length() - keptTail.length());
		return keptHead + elision(omitted) + keptTail;
	}

	/** @return the text up to and including its last line feed, or empty when it carries none */
	private static String throughLastLine(String text) {
		int lastLineFeed = text.lastIndexOf(LINE_FEED);
		return lastLineFeed < 0 ? "" : text.substring(0, lastLineFeed + 1);
	}

	/** @return the text after its first line feed, or empty when it carries none */
	private static String fromFirstLine(String text) {
		int firstLineFeed = text.indexOf(LINE_FEED);
		return firstLineFeed < 0 ? "" : text.substring(firstLineFeed + 1);
	}

	private static String elision(long omitted) {
		return "[... " + omitted + " characters of output omitted ...]" + System.lineSeparator();
	}

	private static int requirePositive(int limit, String field) {
		if (limit <= 0) {
			throw new IllegalArgumentException(field + " must be positive but was " + limit);
		}
		return limit;
	}
}

package io.github.adamw7.tools.enforcer.text;

/**
 * Small helpers for reading Markdown content. Shared by the enforcer rules so
 * byte-order-mark stripping and first-line detection behave identically
 * everywhere.
 */
public final class MarkdownText {

	private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

	private MarkdownText() {
	}

	/** Removes a single leading UTF-8 byte-order mark, if present. */
	public static String stripByteOrderMark(String content) {
		if (!content.isEmpty() && content.charAt(0) == BYTE_ORDER_MARK) {
			return content.substring(1);
		}
		return content;
	}

	/** The first line that is not blank, stripped of surrounding whitespace, or empty if none. */
	public static String firstNonBlankLine(String content) {
		return content.lines()
				.map(String::strip)
				.filter(line -> !line.isEmpty())
				.findFirst()
				.orElse("");
	}
}

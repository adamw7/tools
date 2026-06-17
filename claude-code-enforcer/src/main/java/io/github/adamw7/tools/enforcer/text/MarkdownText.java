package io.github.adamw7.tools.enforcer.text;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * Small helpers for reading Markdown content. Shared by the enforcer rules so
 * file reading, byte-order-mark stripping and first-line detection behave
 * identically everywhere.
 */
public final class MarkdownText {

	private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

	private MarkdownText() {
	}

	/**
	 * Reads {@code file} as a UTF-8 document with any leading byte-order mark
	 * removed. An {@link IOException} is wrapped in an {@link UncheckedIOException}
	 * describing the document, so every rule reports a read failure the same way.
	 */
	public static String read(File file, String description) {
		try {
			return stripByteOrderMark(Files.readString(file.toPath()));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + description + " at " + file, e);
		}
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

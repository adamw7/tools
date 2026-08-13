package io.github.adamw7.tools.markdown;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Small helpers for reading Markdown content. Shared by the enforcer rules so
 * file reading, byte-order-mark stripping and first-line detection behave
 * identically everywhere.
 */
public final class MarkdownText {

	private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
	private static final Pattern CODE_SPAN = Pattern.compile("`[^`]*`");

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

	/**
	 * Reads {@code file} the way {@link #read} does, but yields empty instead of
	 * throwing when it cannot be decoded as text. A rule that scans a directory it
	 * does not control meets binary and non-UTF-8 files, and an
	 * {@link UncheckedIOException} escaping a rule aborts the build as an internal
	 * error rather than as the violation the rule exists to report.
	 */
	public static Optional<String> readIfText(File file) {
		return readIfTextWithByteOrderMark(file).map(MarkdownText::stripByteOrderMark);
	}

	/**
	 * Reads {@code file} the way {@link #readIfText} does but keeps a leading
	 * byte-order mark, for a rule that must judge the file as the program that runs
	 * it will read it rather than as a document. A shell script is the case in
	 * point: the kernel reads bytes, so a mark in front of the {@code #!} line makes
	 * the script unrunnable, and a reader that strips the mark first can never see
	 * that — it reported such a script as well formed.
	 */
	public static Optional<String> readIfTextWithByteOrderMark(File file) {
		try {
			return Optional.of(Files.readString(file.toPath()));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/**
	 * Writes {@code content} to {@code file} as UTF-8, overwriting any existing
	 * content. A path that is a symbolic link is refused rather than followed, so
	 * an auto-fix cannot be redirected through a planted link to rewrite a file
	 * outside the definitions it is repairing. An {@link IOException} is wrapped in
	 * an {@link UncheckedIOException} describing the document, mirroring
	 * {@link #read} so a write failure surfaces the same way.
	 */
	public static void write(File file, String content, String description) {
		try {
			Path path = file.toPath();
			if (Files.isSymbolicLink(path)) {
				throw new IOException("refusing to write through a symbolic link");
			}
			Files.writeString(path, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + description + " at " + file, e);
		}
	}

	/** Removes a single leading UTF-8 byte-order mark, if present. */
	public static String stripByteOrderMark(String content) {
		if (startsWithByteOrderMark(content)) {
			return content.substring(1);
		}
		return content;
	}

	/** True when {@code content} still carries the mark {@link #stripByteOrderMark} removes. */
	public static boolean startsWithByteOrderMark(String content) {
		return !content.isEmpty() && content.charAt(0) == BYTE_ORDER_MARK;
	}

	/**
	 * The line with every inline code span replaced by a space, so text quoted as
	 * code is not read as the markup it illustrates. Documentation about Markdown
	 * writes a sample link or import inside backticks precisely because it is an
	 * example, and a rule that followed it would report the sample's target as a
	 * missing file.
	 */
	public static String withoutCodeSpans(String line) {
		return CODE_SPAN.matcher(line).replaceAll(" ");
	}

	/**
	 * The first line that is not blank, stripped of surrounding whitespace, or empty
	 * if none. Takes the lines rather than the content, because every caller already
	 * holds them: a document has split its own, and a caller that has not can pass
	 * {@code content.lines()}.
	 */
	public static String firstNonBlankLine(Stream<String> lines) {
		return lines.map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
	}
}

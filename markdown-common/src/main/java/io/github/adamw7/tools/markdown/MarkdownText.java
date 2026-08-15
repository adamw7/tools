package io.github.adamw7.tools.markdown;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Small helpers for reading Markdown content. Shared by the enforcer rules so
 * file reading, byte-order-mark stripping and first-line detection behave
 * identically everywhere.
 */
public final class MarkdownText {

	private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
	private static final char BACKTICK = '`';

	/** What a code span is replaced by, so the words either side of it stay separate. */
	private static final String SPAN_REPLACEMENT = " ";

	/** No run of backticks closes this one. */
	private static final int UNCLOSED = -1;

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
	 * <p>
	 * A span is delimited as Markdown delimits one: a run of backticks is closed by
	 * the next run of <em>exactly</em> the same length. Both halves of that matter,
	 * because a span whose own content carries a backtick has to be written with a
	 * longer run — {@code ``a ` b``} is the only way to quote {@code a ` b} at all.
	 * Reading the delimiter as a single backtick instead tore such a span in half at
	 * its second character and handed the content back as prose: a document that
	 * wrote {@code ``TODO``} was reported as carrying the very token it had quoted,
	 * a sample import written {@code ``@docs/setup.md``} was followed to a file
	 * nobody meant to name, and a {@code ``<!--``} shown as an example opened a
	 * comment nothing closed — masking every line below it, the document's own
	 * headings included.
	 * <p>
	 * A run that no later run of its length closes is ordinary text, and the scan
	 * carries on from the run after it, so an odd backtick neither swallows the rest
	 * of the line nor hides a span written further along it.
	 */
	public static String withoutCodeSpans(String line) {
		List<Backticks> runs = backtickRuns(line);
		StringBuilder text = new StringBuilder();
		int copied = 0;
		int index = 0;
		while (index < runs.size()) {
			int closing = closingRun(runs, index);
			if (closing == UNCLOSED) {
				index++;
			} else {
				text.append(line, copied, runs.get(index).start()).append(SPAN_REPLACEMENT);
				copied = runs.get(closing).end();
				index = closing + 1;
			}
		}
		return text.append(line, copied, line.length()).toString();
	}

	/** One maximal run of backticks: where it starts, and how many it runs. */
	private record Backticks(int start, int length) {

		int end() {
			return start + length;
		}
	}

	/**
	 * The line's runs of backticks, in order. They are maximal, so two of them are
	 * always separated by at least one other character — which is what lets a run be
	 * compared with the next one by length alone.
	 */
	private static List<Backticks> backtickRuns(String line) {
		List<Backticks> runs = new ArrayList<>();
		int index = 0;
		while (index < line.length()) {
			int length = runLengthAt(line, index);
			if (length > 0) {
				runs.add(new Backticks(index, length));
			}
			index += Math.max(length, 1);
		}
		return runs;
	}

	private static int runLengthAt(String line, int start) {
		int end = start;
		while (end < line.length() && line.charAt(end) == BACKTICK) {
			end++;
		}
		return end - start;
	}

	/** The first later run of the same length, or {@link #UNCLOSED} when none closes this one. */
	private static int closingRun(List<Backticks> runs, int opening) {
		int length = runs.get(opening).length();
		return IntStream.range(opening + 1, runs.size())
				.filter(index -> runs.get(index).length() == length)
				.findFirst()
				.orElse(UNCLOSED);
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

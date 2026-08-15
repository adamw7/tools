package io.github.adamw7.tools.markdown;

import static io.github.adamw7.tools.test.TestFiles.assumeSymlink;
import static io.github.adamw7.tools.test.TestFiles.readString;
import static io.github.adamw7.tools.test.TestFiles.writeBytes;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownTextTest {

	private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

	@TempDir
	private Path tempDir;

	@Test
	void firstNonBlankLineSkipsLeadingBlanksAndStrips() {
		assertEquals("# Title", MarkdownText.firstNonBlankLine("\n   \n   # Title  \nbody".lines()));
	}

	@Test
	void firstNonBlankLineIsEmptyWhenAllBlank() {
		assertEquals("", MarkdownText.firstNonBlankLine("\n   \n\t\n".lines()));
	}

	@Test
	void firstNonBlankLineIsEmptyForEmptyContent() {
		assertEquals("", MarkdownText.firstNonBlankLine("".lines()));
	}

	@Test
	void firstNonBlankLineStripsAndPicksTheFirstContentLine() {
		assertEquals("# Title", MarkdownText.firstNonBlankLine(Stream.of("", "   ", "  # Title  ", "body")));
	}

	@Test
	void firstNonBlankLineIsEmptyWhenNoContentLine() {
		assertEquals("", MarkdownText.firstNonBlankLine(Stream.of("", "   ", "\t")));
	}

	@Test
	void stripByteOrderMarkRemovesALeadingMark() {
		assertEquals("# Title", MarkdownText.stripByteOrderMark(BYTE_ORDER_MARK + "# Title"));
	}

	@Test
	void stripByteOrderMarkLeavesContentWithoutAMarkUnchanged() {
		String content = "# Title";

		assertSame(content, MarkdownText.stripByteOrderMark(content));
	}

	@Test
	void stripByteOrderMarkHandlesEmptyContent() {
		assertEquals("", MarkdownText.stripByteOrderMark(""));
	}

	@Test
	void readReturnsFileContentWithTheByteOrderMarkStripped() {
		Path file = tempDir.resolve("doc.md");
		writeString(file, BYTE_ORDER_MARK + "# Title\nbody");

		assertEquals("# Title\nbody", MarkdownText.read(file.toFile(), "doc.md"));
	}

	@Test
	void startsWithByteOrderMarkAnswersForBothSpellings() {
		assertTrue(MarkdownText.startsWithByteOrderMark(BYTE_ORDER_MARK + "#!/bin/sh"));
		assertFalse(MarkdownText.startsWithByteOrderMark("#!/bin/sh"));
		assertFalse(MarkdownText.startsWithByteOrderMark(""));
	}

	/** A rule judging a script has to see the mark the kernel sees, so this read keeps it. */
	@Test
	void readIfTextWithByteOrderMarkKeepsTheMarkThatReadIfTextStrips() {
		Path file = tempDir.resolve("hook.sh");
		writeString(file, BYTE_ORDER_MARK + "#!/bin/sh\n");

		assertEquals(BYTE_ORDER_MARK + "#!/bin/sh\n",
				MarkdownText.readIfTextWithByteOrderMark(file.toFile()).orElseThrow());
		assertEquals("#!/bin/sh\n", MarkdownText.readIfText(file.toFile()).orElseThrow());
	}

	@Test
	void readIfTextWithByteOrderMarkIsEmptyForAFileThatIsNotText() {
		Path file = tempDir.resolve("logo.png");
		writeBytes(file, new byte[] { (byte) 0x89, 0x50, (byte) 0xFF, (byte) 0xFE });

		assertTrue(MarkdownText.readIfTextWithByteOrderMark(file.toFile()).isEmpty());
	}

	@Test
	void writePersistsContentToARegularFile() {
		Path file = tempDir.resolve("out.md");

		MarkdownText.write(file.toFile(), "# Title\nbody", "out.md");

		assertEquals("# Title\nbody", readString(file));
	}

	@Test
	void writeRefusesToFollowASymbolicLink() {
		Path target = tempDir.resolve("target.md");
		writeString(target, "original");
		Path link = tempDir.resolve("link.md");
		assumeSymlink(link, target);

		assertFailure(UncheckedIOException.class, () -> MarkdownText.write(link.toFile(), "overwritten", "link.md"),
				"link.md");
		assertEquals("original", readString(target));
	}

	@Test
	void withoutCodeSpansReplacesASingleBacktickSpan() {
		assertEquals("never leave a   behind", MarkdownText.withoutCodeSpans("never leave a `TODO` behind"));
	}

	/**
	 * A run of two backticks is closed by a run of two, not by the second half of the
	 * run that opened it. Reading the delimiter as one backtick tore the span apart at
	 * its second character and handed {@code TODO} back as prose, so a rule forbidding
	 * that token reported the very document that had quoted it.
	 */
	@Test
	void withoutCodeSpansReplacesASpanWrittenWithALongerRun() {
		assertEquals("never leave a   behind", MarkdownText.withoutCodeSpans("never leave a ``TODO`` behind"));
		assertEquals("a   b", MarkdownText.withoutCodeSpans("a ```TODO``` b"));
	}

	/** The reason a longer run exists: it is the only way to quote content carrying a backtick. */
	@Test
	void withoutCodeSpansReplacesASpanCarryingABacktickOfItsOwn() {
		assertEquals("a   b", MarkdownText.withoutCodeSpans("a ``x ` y`` b"));
	}

	/** A run of one does not close a run of three, so neither delimiter is read as the other's. */
	@Test
	void withoutCodeSpansLeavesARunNoLaterRunOfItsLengthCloses() {
		assertEquals("a ``` x ` y", MarkdownText.withoutCodeSpans("a ``` x ` y"));
	}

	/**
	 * The scan carries on past a run nothing closes rather than stopping at it, so an
	 * odd backtick earlier in the line does not hide a real span further along it.
	 */
	@Test
	void withoutCodeSpansReadsASpanFollowingAnUnclosedRun() {
		assertEquals("`` a   b", MarkdownText.withoutCodeSpans("`` a `TODO` b"));
	}

	@Test
	void withoutCodeSpansReplacesEverySpanOnTheLine() {
		assertEquals("  and   and  ", MarkdownText.withoutCodeSpans("`a` and ``b`` and `c`"));
	}

	@Test
	void withoutCodeSpansLeavesALineCarryingNoBacktickUnchanged() {
		assertEquals("plain prose", MarkdownText.withoutCodeSpans("plain prose"));
		assertEquals("", MarkdownText.withoutCodeSpans(""));
	}

	@Test
	void readWrapsAReadFailureWithTheDescription() {
		Path missing = tempDir.resolve("absent.md");

		assertFailure(UncheckedIOException.class, () -> MarkdownText.read(missing.toFile(), "absent.md"),
				"absent.md");
	}
}

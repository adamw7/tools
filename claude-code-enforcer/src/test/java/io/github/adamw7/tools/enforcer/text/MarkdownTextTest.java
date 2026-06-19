package io.github.adamw7.tools.enforcer.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
		assertEquals("# Title", MarkdownText.firstNonBlankLine("\n   \n   # Title  \nbody"));
	}

	@Test
	void firstNonBlankLineIsEmptyWhenAllBlank() {
		assertEquals("", MarkdownText.firstNonBlankLine("\n   \n\t\n"));
	}

	@Test
	void firstNonBlankLineIsEmptyForEmptyContent() {
		assertEquals("", MarkdownText.firstNonBlankLine(""));
	}

	@Test
	void streamOverloadMatchesTheStringOverload() {
		String content = "\n   \n   # Title  \nbody";

		assertEquals(MarkdownText.firstNonBlankLine(content),
				MarkdownText.firstNonBlankLine(content.lines()));
	}

	@Test
	void streamOverloadStripsAndPicksTheFirstContentLine() {
		assertEquals("# Title", MarkdownText.firstNonBlankLine(Stream.of("", "   ", "  # Title  ", "body")));
	}

	@Test
	void streamOverloadIsEmptyWhenNoContentLine() {
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
	void readWrapsAReadFailureWithTheDescription() {
		Path missing = tempDir.resolve("absent.md");

		UncheckedIOException exception = assertThrows(UncheckedIOException.class,
				() -> MarkdownText.read(missing.toFile(), "absent.md"));
		assertTrue(exception.getMessage().contains("absent.md"), exception.getMessage());
	}

	private static void writeString(Path file, String content) {
		try {
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write " + file, e);
		}
	}
}

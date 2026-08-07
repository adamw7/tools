package io.github.adamw7.tools.enforcer.text;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.assumeSymlink;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.readString;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;
import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
	void readWrapsAReadFailureWithTheDescription() {
		Path missing = tempDir.resolve("absent.md");

		assertFailure(UncheckedIOException.class, () -> MarkdownText.read(missing.toFile(), "absent.md"),
				"absent.md");
	}
}

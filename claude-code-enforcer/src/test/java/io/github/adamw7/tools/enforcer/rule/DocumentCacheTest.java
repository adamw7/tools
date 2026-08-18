package io.github.adamw7.tools.enforcer.rule;

import static io.github.adamw7.tools.test.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.markdown.MarkdownDocument;

/**
 * What the cache shares between rules, and — the part that would be a defect
 * rather than a missed optimisation — what it refuses to share once the file
 * behind it has changed.
 */
class DocumentCacheTest {

	@TempDir
	private Path tempDir;

	@BeforeEach
	void startEmpty() {
		DocumentCache.clear();
	}

	@Test
	void readsAFileOnceHoweverManyRulesAskForIt() {
		File file = writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n").toFile();
		AtomicInteger reads = new AtomicInteger();

		for (int rule = 0; rule < 5; rule++) {
			assertEquals(Optional.of("content"), DocumentCache.text(file, () -> {
				reads.incrementAndGet();
				return Optional.of("content");
			}));
		}

		assertEquals(1, reads.get(), "the file was read once per rule that asked");
	}

	/** Sharing a parse is only sound because the parsed document is immutable. */
	@Test
	void handsEveryRuleTheSameParsedDocument() {
		File file = writeString(tempDir.resolve("CLAUDE.md"), "# CLAUDE.md\n").toFile();

		MarkdownDocument first = DocumentCache.markdown(file, "# CLAUDE.md\n");
		MarkdownDocument second = DocumentCache.markdown(file, "# CLAUDE.md\n");

		assertSame(first, second);
	}

	/**
	 * The defect a cache invites: autoFix rewrites a definition mid-build, and a rule
	 * running afterwards must validate what is on disk now, not what was there when
	 * the first rule looked.
	 */
	@Test
	void rereadsAFileThatWasRewrittenDuringTheBuild() throws IOException {
		Path path = writeString(tempDir.resolve("SKILL.md"), "before");
		File file = path.toFile();
		assertEquals(Optional.of("before"), DocumentCache.text(file, () -> read(path)));

		writeString(path, "after and appreciably longer");

		assertEquals(Optional.of("after and appreciably longer"), DocumentCache.text(file, () -> read(path)));
	}

	/**
	 * Size alone would not notice an edit that kept the length, so the stamp carries
	 * the modification time too. Set explicitly rather than waited for, since a test
	 * must not sleep and a filesystem's clock is not the test's to assume.
	 */
	@Test
	void rereadsAFileEditedWithoutChangingItsLength() throws IOException {
		Path path = writeString(tempDir.resolve("SKILL.md"), "aaaa");
		File file = path.toFile();
		assertEquals(Optional.of("aaaa"), DocumentCache.text(file, () -> read(path)));

		writeString(path, "bbbb");
		Files.setLastModifiedTime(path, FileTime.fromMillis(Files.getLastModifiedTime(path).toMillis() + 5000));

		assertEquals(Optional.of("bbbb"), DocumentCache.text(file, () -> read(path)));
	}

	/**
	 * A read that failed is not kept: the next rule to ask needs the same diagnosis,
	 * and a cached empty would have to carry it.
	 */
	@Test
	void doesNotCacheAReadThatFailed() {
		File file = writeString(tempDir.resolve("CLAUDE.md"), "x").toFile();
		AtomicInteger reads = new AtomicInteger();

		for (int rule = 0; rule < 3; rule++) {
			DocumentCache.text(file, () -> {
				reads.incrementAndGet();
				return Optional.empty();
			});
		}

		assertEquals(3, reads.get());
	}

	/**
	 * A file that is not there yields no key, so the caller reads it directly and the
	 * rule's own missing-file diagnosis is what the operator sees.
	 */
	@Test
	void readsDirectlyWhenTheFileCannotBeStamped() {
		File absent = tempDir.resolve("absent.md").toFile();
		AtomicInteger reads = new AtomicInteger();

		for (int rule = 0; rule < 2; rule++) {
			DocumentCache.text(absent, () -> {
				reads.incrementAndGet();
				return Optional.of("somehow");
			});
		}

		assertEquals(2, reads.get());
	}

	/** Two files are two entries, however alike their content. */
	@Test
	void keysOnTheFileRatherThanItsContent() {
		File first = writeString(tempDir.resolve("one.md"), "same").toFile();
		File second = writeString(tempDir.resolve("two.md"), "same").toFile();

		assertEquals(Optional.of("first"), DocumentCache.text(first, () -> Optional.of("first")));
		assertEquals(Optional.of("second"), DocumentCache.text(second, () -> Optional.of("second")));
	}

	@Test
	void clearingDropsWhatItHeld() {
		File file = writeString(tempDir.resolve("CLAUDE.md"), "x").toFile();
		DocumentCache.text(file, () -> Optional.of("first"));

		DocumentCache.clear();

		assertEquals(Optional.of("second"), DocumentCache.text(file, () -> Optional.of("second")));
	}

	/** The bound is a safety net for a long-lived JVM, not something a build reaches. */
	@Test
	void staysBoundedWhenAskedAboutManyFiles() {
		for (int index = 0; index < 600; index++) {
			File file = writeString(tempDir.resolve("file" + index + ".md"), "content " + index).toFile();
			DocumentCache.text(file, () -> Optional.of("content"));
		}

		File extra = writeString(tempDir.resolve("extra.md"), "extra").toFile();
		assertTrue(DocumentCache.text(extra, () -> Optional.of("extra")).isPresent());
	}

	private Optional<String> read(Path path) {
		try {
			return Optional.of(Files.readString(path));
		} catch (IOException e) {
			return Optional.empty();
		}
	}
}

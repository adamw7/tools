package io.github.adamw7.tools.enforcer.doc;

import static io.github.adamw7.tools.test.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.enforcer.doc.ImportGraph.Reference;
import io.github.adamw7.tools.enforcer.rule.ProjectFiles;

/**
 * Tests for the import graph behind {@link MemoryImportsRule}. The hop counts are
 * what the rule's verdict rests on, so they are asserted directly here rather than
 * only through the violations the rule happens to phrase.
 */
class ImportGraphTest {

	private static final Predicate<String> NOTHING_IGNORED = imported -> false;

	@TempDir
	private Path tempDir;

	private final List<String> unreadable = new ArrayList<>();

	@Test
	void theRootSitsZeroHopsFromItself() {
		File root = write("CLAUDE.md", "# CLAUDE.md\n");

		assertEquals(0, graphFrom(root).hopsTo(root));
	}

	@Test
	void countsOneHopForADirectImport() {
		File target = write("docs.md", "# Docs\n");
		File root = write("CLAUDE.md", "See @docs.md\n");

		assertEquals(1, graphFrom(root).hopsTo(target));
	}

	@Test
	void countsAHopPerLinkOfAChain() {
		File deep = write("third.md", "# Third\n");
		write("second.md", "See @third.md\n");
		write("first.md", "See @second.md\n");
		File root = write("CLAUDE.md", "See @first.md\n");

		assertEquals(3, graphFrom(root).hopsTo(deep));
	}

	@Test
	void countsTheShortestChainWhenTheDirectImportComesFirst() {
		File target = write("shared.md", "# Shared\n");
		write("long.md", "See @shared.md\n");
		File root = write("CLAUDE.md", "See @shared.md and @long.md\n");

		assertEquals(1, graphFrom(root).hopsTo(target));
	}

	@Test
	void countsTheShortestChainWhenTheLongChainComesFirst() {
		File target = write("shared.md", "# Shared\n");
		write("long.md", "See @shared.md\n");
		File root = write("CLAUDE.md", "See @long.md and @shared.md\n");

		// The answer must not depend on which import is written first, which is
		// the whole reason the graph is built breadth-first.
		assertEquals(1, graphFrom(root).hopsTo(target));
	}

	@Test
	void reportsAnUnreachableFileAsInfinitelyFarAway() {
		File stranger = write("stranger.md", "# Stranger\n");
		File root = write("CLAUDE.md", "# CLAUDE.md\n");

		assertEquals(Integer.MAX_VALUE, graphFrom(root).hopsTo(stranger));
	}

	@Test
	void keepsAMissingTargetAsAReferenceWithoutAHopCount() {
		File root = write("CLAUDE.md", "See @absent.md\n");
		ImportGraph graph = graphFrom(root);

		assertEquals(List.of("absent.md"), textOf(graph.importsOf(root)));
		assertEquals(Integer.MAX_VALUE, graph.hopsTo(new File(tempDir.toFile(), "absent.md")));
	}

	@Test
	void terminatesOnACycleAndKeepsTheFirstHopCount() {
		File loop = write("loop.md", "Back to @CLAUDE.md\n");
		File root = write("CLAUDE.md", "See @loop.md\n");
		ImportGraph graph = graphFrom(root);

		assertEquals(1, graph.hopsTo(loop));
		assertEquals(0, graph.hopsTo(root));
	}

	@Test
	void keepsImportsInDocumentOrder() {
		write("a.md", "# A\n");
		write("b.md", "# B\n");
		File root = write("CLAUDE.md", "See @b.md then @a.md\n");

		assertEquals(List.of("b.md", "a.md"), textOf(graphFrom(root).importsOf(root)));
	}

	@Test
	void yieldsNoImportsForAFileTheRootNeverReaches() {
		File stranger = write("stranger.md", "See @somewhere.md\n");
		File root = write("CLAUDE.md", "# CLAUDE.md\n");

		assertEquals(List.of(), graphFrom(root).importsOf(stranger));
	}

	@Test
	void leavesOutAnIgnoredImport() {
		File target = write("docs.md", "# Docs\n");
		File root = write("CLAUDE.md", "See @docs.md\n");
		ImportGraph graph = ImportGraph.from(root, "docs.md"::equals, unreadable::add);

		// An ignored import is not an edge at all, so it neither shows up as a
		// reference nor drags its target into the graph.
		assertEquals(List.of(), graph.importsOf(root));
		assertEquals(Integer.MAX_VALUE, graph.hopsTo(target));
	}

	@Test
	void ignoresImportsInsideFencedCodeBlocks() {
		File root = write("CLAUDE.md", "# CLAUDE.md\n\n```\n@docs.md\n```\n");

		assertEquals(List.of(), graphFrom(root).importsOf(root));
	}

	@Test
	void ignoresImportsInsideInlineCodeSpans() {
		File root = write("CLAUDE.md", "an `@claude`-mention workflow\n");

		assertEquals(List.of(), graphFrom(root).importsOf(root));
	}

	/**
	 * A span written with a longer run of backticks quotes its content just as firmly.
	 * Closing the run on its own second character let the path out of the span it was
	 * shown in, so a sample import was followed to a file nobody meant to name and the
	 * rule demanded it exist.
	 */
	@Test
	void ignoresImportsInsideLongerInlineCodeSpans() {
		File root = write("CLAUDE.md", "An import is written ``@docs/setup.md``.\n");

		assertEquals(List.of(), graphFrom(root).importsOf(root));
	}

	@Test
	void ignoresAHomeRelativeImport() {
		File root = write("CLAUDE.md", "Also @~/personal/prefs.md is loaded.\n");

		assertEquals(List.of(), graphFrom(root).importsOf(root));
	}

	/**
	 * Only a leading {@code ~} is home-relative. Windows shortens a long directory
	 * name to an 8.3 one — {@code RUNNER~1} — so a path a document spells on such a
	 * machine carries a {@code ~} the import syntax has to be able to write.
	 */
	@Test
	void followsAnImportThroughAPathSegmentCarryingATilde() {
		File target = write("RUNNER~1/notes.md", "# Notes\n");
		File root = write("CLAUDE.md", "See @RUNNER~1/notes.md\n");
		ImportGraph graph = graphFrom(root);

		assertEquals(List.of("RUNNER~1/notes.md"), textOf(graph.importsOf(root)));
		assertEquals(1, graph.hopsTo(target));
	}

	@Test
	void dropsSentencePunctuationFromTheImportPath() {
		File root = write("CLAUDE.md", "See @docs.md.\n");

		assertEquals(List.of("docs.md"), textOf(graphFrom(root).importsOf(root)));
	}

	@Test
	void resolvesARelativeImportAgainstTheImportingFile() {
		File sibling = write("docs/sibling.md", "# Sibling\n");
		write("docs/inner.md", "See @sibling.md\n");
		File root = write("CLAUDE.md", "See @docs/inner.md\n");

		assertEquals(2, graphFrom(root).hopsTo(sibling));
	}

	@Test
	void resolvesAnAbsoluteImportAsAnAbsolutePath() {
		File absolute = write("elsewhere.md", "# Elsewhere\n");
		File root = write("CLAUDE.md", "See @" + rooted(absolute) + "\n");

		// A leading slash means the path is not relative to the importing file.
		assertEquals(1, graphFrom(root).hopsTo(absolute));
	}

	@Test
	void resolvesARootedImportAgainstTheImportingFilesRoot() {
		File root = write("CLAUDE.md", "See @/elsewhere.md\n");

		File target = graphFrom(root).importsOf(root).get(0).target();

		// The root the importing file sits on, not the one the build was launched
		// from — the two differ on a file system with more than one root.
		assertEquals(tempDir.getRoot().resolve("elsewhere.md"), ProjectFiles.normalized(target));
	}

	@Test
	void treatsAnImportTargetThatIsNotTextAsALeaf() throws IOException {
		Path binary = tempDir.resolve("logo.md");
		Files.write(binary, new byte[] { (byte) 0xC3, (byte) 0x28, (byte) 0xA0 });
		File root = write("CLAUDE.md", "See @logo.md\n");
		ImportGraph graph = graphFrom(root);

		// Its existence still counts — an imported file may be any format — but it
		// contributes no imports of its own.
		assertEquals(1, graph.hopsTo(binary.toFile()));
		assertEquals(List.of(), graph.importsOf(binary.toFile()));
	}

	@Test
	void announcesAnImportTargetThatCannotBeReadAsText() throws IOException {
		Path binary = tempDir.resolve("logo.md");
		Files.write(binary, new byte[] { (byte) 0xC3, (byte) 0x28, (byte) 0xA0 });
		File root = write("CLAUDE.md", "See @logo.md\n");

		graphFrom(root);

		assertEquals(1, unreadable.size(), unreadable.toString());
		assertTrue(unreadable.get(0).startsWith("Skipping unreadable import target"), unreadable.toString());
		assertTrue(unreadable.get(0).contains("logo.md"), unreadable.toString());
	}

	@Test
	void saysNothingAboutFilesItCanRead() {
		write("docs.md", "# Docs\n");
		File root = write("CLAUDE.md", "See @docs.md\n");

		graphFrom(root);

		assertTrue(unreadable.isEmpty(), unreadable.toString());
	}

	@Test
	void normalizesAwayDotSegments() {
		File root = write("CLAUDE.md", "# CLAUDE.md\n");
		File roundabout = new File(tempDir.toFile(), "docs/../CLAUDE.md");

		assertEquals(ProjectFiles.normalized(root), ProjectFiles.normalized(roundabout));
		assertFalse(roundabout.getPath().equals(ProjectFiles.normalized(roundabout).toString()));
	}

	@Test
	void readsNoImportFromABareWordCarryingNeitherSeparatorNorExtension() {
		File root = write("CLAUDE.md", "An @claude-mention workflow, opened by @adamw7.\n");

		assertEquals(List.of(), graphFrom(root).importsOf(root));
	}

	@Test
	void stillReadsAnImportNamedOnlyByItsExtension() {
		write("AGENTS.md", "# Agents\n");
		File root = write("CLAUDE.md", "See @AGENTS.md for the rest.\n");

		assertEquals(List.of("AGENTS.md"), textOf(graphFrom(root).importsOf(root)));
	}

	private ImportGraph graphFrom(File root) {
		return ImportGraph.from(root, NOTHING_IGNORED, unreadable::add);
	}

	private List<String> textOf(List<Reference> references) {
		return references.stream().map(Reference::text).toList();
	}

	private File write(String name, String content) {
		return writeString(tempDir.resolve(name), content).toFile();
	}

	/**
	 * The file as an import writes it: from the file system root, with forward
	 * slashes. A platform's own absolute path is not import syntax — a Windows one
	 * carries a drive letter and backslashes, neither of which an import path
	 * matches.
	 */
	private String rooted(File file) {
		Path path = file.toPath().toAbsolutePath();
		return "/" + path.getRoot().relativize(path).toString().replace(File.separatorChar, '/');
	}
}

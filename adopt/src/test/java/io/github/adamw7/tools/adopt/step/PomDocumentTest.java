package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * Pins what the reader has to get right for an edit to land where it was meant
 * to. The offsets an addition is spliced at are the parser's, so text that merely
 * looks like markup — inside a comment, inside a CDATA section, or inside an
 * attribute value — must not be read as an element: taking one for real would
 * shift every following offset and splice the adoption's block into the middle of
 * something else.
 *
 * <p>What the added block itself looks like is {@link PomEnforcerInstallerTest}'s
 * to assert; these tests add a marker element and ask only where it landed.
 */
class PomDocumentTest {

	private static final String MARKER = "<marker/>";

	@Test
	void doesNotReadMarkupInsideACommentAsAnElement(@TempDir Path dir) throws IOException {
		String pom = """
				<project>
				  <build>
				    <!-- <plugins><plugin/></plugins> -->
				  </build>
				</project>
				""";

		assertTrue(read(dir, pom).at(List.of("build", "plugins")).isEmpty(), "a commented-out element is not one");
		assertTrue(edited(dir, pom).contains("-->\n    " + MARKER + "\n  </build>"), "the edit must follow the comment");
	}

	@Test
	void doesNotReadMarkupInsideACdataSectionAsAnElement(@TempDir Path dir) throws IOException {
		String pom = """
				<project>
				  <build><![CDATA[</build><plugins/>]]></build>
				</project>
				""";

		assertTrue(read(dir, pom).at(List.of("build", "plugins")).isEmpty(), "CDATA content is text, not markup");
		assertTrue(edited(dir, pom).contains("]]>\n    " + MARKER + "</build>"), "the edit must follow the CDATA");
	}

	/**
	 * A {@code >} inside an attribute value does not close the start tag. Reading it
	 * as if it did would put the element's content — and so the edit — at an offset
	 * inside the tag itself.
	 */
	@Test
	void doesNotEndAStartTagAtAGreaterThanInsideAnAttributeValue(@TempDir Path dir) throws IOException {
		String result = edited(dir, """
				<project>
				  <build note="a > b">
				    <plugins/>
				  </build>
				</project>
				""");

		assertTrue(result.contains("<build note=\"a > b\">"), "the attribute must survive verbatim: " + result);
		assertTrue(result.contains("<plugins/>\n    " + MARKER + "\n  </build>"), result);
	}

	@Test
	void aPomThatDeclaresNoRootElementIsRejected(@TempDir Path dir) throws IOException {
		Path pom = write(dir, "<!-- nothing but a comment -->\n");

		assertThrows(AdoptionException.class, () -> PomDocument.read(pom));
	}

	private PomDocument read(Path dir, String content) throws IOException {
		return PomDocument.read(write(dir, content));
	}

	/** Adds a marker under {@code build}, the way an installer adds its plugin, and answers the rewritten file. */
	private String edited(Path dir, String content) throws IOException {
		Path pom = write(dir, content);
		PomDocument document = PomDocument.read(pom);
		document.insertUnder(document.root(), List.of("build"), MARKER);
		document.write();
		return Files.readString(pom);
	}

	private Path write(Path dir, String content) throws IOException {
		Path pom = dir.resolve("pom.xml");
		Files.writeString(pom, content);
		return pom;
	}
}

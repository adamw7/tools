package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.markdown.MarkdownDocument;

/**
 * The choices the adoption's {@code CLAUDE.md} contract makes: which sections, which
 * stub, which reference line, and what a whole document comes back as. How the
 * reshape reads fences, indented code and HTML comments is markdown-common's, and
 * tested there in {@code MarkdownConformerReadingTest} with this same contract.
 */
class ClaudeMdConformerTest {

	private final ClaudeMdConformer conformer = new ClaudeMdConformer();

	/**
	 * The headings a reshaped document carries, read with {@link MarkdownDocument} —
	 * the very reader {@link ClaudeMdConformer} rewrites through and the
	 * {@code claudeMdFormat} rule judges the result with. So a heading inside a fence,
	 * an indented sample or an HTML comment is code here exactly as it is there, and a
	 * heading is the text it names rather than the line it was typed on.
	 *
	 * <p>These tests used to spell that reading out again in a hundred lines of
	 * fence and comment tracking of their own, which is a third implementation of a
	 * question two production classes already answer together — kept equal to them
	 * only by being edited alongside them. What keeps the reshape honest against an
	 * <em>independent</em> reader is {@link ClaudeMdConformerContractTest}, which runs
	 * the real rule over the real output.
	 *
	 * <p>Duplicates are kept, unlike {@link MarkdownDocument#headings()}, because a
	 * section written twice is exactly what some of these tests are about.
	 */
	private List<String> headings(String content) {
		MarkdownDocument document = MarkdownDocument.parse(content);
		return document.structuralLines(line -> MarkdownDocument.headingOf(line).isPresent())
				.mapToObj(document::line)
				.map(MarkdownDocument::headingOf)
				.flatMap(Optional::stream)
				.toList();
	}

	/** Whether the section carries a body, asked exactly as the rule asks it. */
	private boolean hasBody(String content, String section) {
		MarkdownDocument document = MarkdownDocument.parse(content);
		int index = document.headingIndex(section);
		return index >= 0 && document.hasBodyAt(index);
	}

	@Test
	void canonicalisesNearMissHeadingsInPlacePreservingBody() {
		String generated = """
				# CLAUDE.md

				## Project purpose

				A security playground.

				## Java version

				Java 25.

				## Maven module structure

				Root pom is packaging=pom.

				## Principles for Java development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";
		String conformed = conformer.conform(generated);
		List<String> headings = headings(conformed);
		assertTrue(headings.containsAll(ClaudeMdConformer.REQUIRED_SECTIONS), headings.toString());
		assertFalse(conformed.contains("## Project purpose"), "the near-miss heading should be renamed, not duplicated");
		assertFalse(conformed.contains("## Maven module structure"), "the near-miss heading should be renamed");
		assertTrue(conformed.contains("A security playground."), "the renamed section keeps its body");
		assertTrue(conformed.contains("Root pom is packaging=pom."), "the renamed section keeps its body");
	}

	/**
	 * A heading is only half of what the rule checks: it fails an empty section
	 * just as it fails a missing one, so a near-miss renamed over a bare section
	 * has to come out with a body or the adoption fails its own verification.
	 */
	@Test
	void givesARenamedNearMissWithNoContentAStubBody() {
		String generated = """
				# CLAUDE.md

				## Project purpose

				## Build commands

				Run `mvn install`.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains("## Project\n\n" + ClaudeMdConformer.STUB_BODY),
				"the emptied section must be given a body:\n" + conformed);
		ClaudeMdConformer.REQUIRED_SECTIONS.forEach(section -> assertTrue(hasBody(conformed, section),
				section + " must have a body:\n" + conformed));
	}

	/**
	 * The stub used to read "See [AGENTS.md](AGENTS.md).", while the {@code AGENTS.md}
	 * installed beside it refers the reader back to {@code CLAUDE.md} and calls it the
	 * source of truth. Every adopted repository's pull request therefore arrived with
	 * six sections that said nothing and pointed in a circle, and a maintainer could
	 * not tell a section nobody had answered from one that had been.
	 */
	@Test
	void stubsSayNothingIsRecordedRatherThanPointingAtTheCompanionFile() {
		String conformed = conformer.conform("# CLAUDE.md\n");
		assertFalse(conformed.contains("## Project\n\nSee [AGENTS.md](AGENTS.md)."),
				"a stub must not send the reader to the file that sends them back:\n" + conformed);
		assertTrue(conformed.contains(ClaudeMdConformer.STUB_BODY), "the stub must still be there:\n" + conformed);
		assertEquals(1, conformed.lines().filter(line -> line.contains("AGENTS.md")).count(),
				"AGENTS.md is named once, by the reference line the rule demands:\n" + conformed);
	}

	@Test
	void keepsTheBodyOfASectionThatAlreadyHasOne() {
		String generated = "# CLAUDE.md\n\n" + requiredSectionsBody();
		String conformed = conformer.conform(generated);
		assertEquals(ClaudeMdConformer.REQUIRED_SECTIONS.size(), conformed.split("Content\\.", -1).length - 1,
				"every original body must survive exactly once:\n" + conformed);
		assertFalse(conformed.contains("Content.\n\n" + ClaudeMdConformer.STUB_BODY),
				"a section that already has a body must not be given a stub");
	}

	@Test
	void insertsAgentsReferenceWhenAbsent() {
		String generated = "# CLAUDE.md\n\n" + requiredSectionsBody();
		assertFalse(generated.contains(ClaudeMdConformer.AGENTS_REFERENCE));
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains(ClaudeMdConformer.AGENTS_REFERENCE), "an AGENTS.md reference must be added");
		assertEquals(ClaudeMdConformer.TITLE, conformed.lines().findFirst().orElseThrow(),
				"the title must stay the first line");
	}

	@Test
	void appendsAStubForAGenuinelyMissingSection() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Project

				A repo.

				## Java version

				Java 25.

				## Maven

				Maven.

				## Principles for Java Development

				SOLID.

				## Dependencies

				Existing only.
				""";
		assertFalse(generated.contains("## Testing"));
		String conformed = conformer.conform(generated);
		assertTrue(headings(conformed).contains("## Testing"), "the missing section is scaffolded");
		assertTrue(headings(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS));
	}

	@Test
	void addsTheTitleWhenTheDocumentDoesNotStartWithIt() {
		String generated = "## Project\n\nA repo.\n";
		String conformed = conformer.conform(generated);
		assertEquals(ClaudeMdConformer.TITLE, conformed.lines().findFirst().orElseThrow());
	}

	@Test
	void leavesAnAlreadyConformingDocumentUnchanged() {
		String conforming = conforming();
		String conformed = conformer.conform(conforming);
		assertEquals(conforming, conformed, "a conforming document must be a no-op");
	}

	/** A document the reshape has nothing to do to, for the tests that are about what it leaves alone. */
	private String conforming() {
		return ("# CLAUDE.md\n\n" + ClaudeMdConformer.AGENTS_REFERENCE_LINE + "\n\n"
				+ requiredSectionsBody()).stripTrailing() + "\n";
	}

	@Test
	void normalisationIsIdempotent() {
		String generated = """
				# CLAUDE.md

				## Project purpose

				A repo.

				## Java version

				Java 25.

				## Maven module structure

				Maven.

				## Principles for Java development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";
		String once = conformer.conform(generated);
		String twice = conformer.conform(once);
		assertEquals(once, twice, "re-running the conformer must not churn the file");
	}

	@Test
	void buildsAWholeSkeletonFromAnEmptyDocument() {
		String conformed = conformer.conform("");
		assertEquals(ClaudeMdConformer.TITLE, conformed.lines().findFirst().orElseThrow());
		assertTrue(headings(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS), conformed);
		ClaudeMdConformer.REQUIRED_SECTIONS.forEach(section -> assertTrue(hasBody(conformed, section),
				section + " must have a body:\n" + conformed));
	}

	private String requiredSectionsBody() {
		return ClaudeMdConformer.REQUIRED_SECTIONS.stream()
				.map(section -> section + "\n\nContent.\n\n")
				.collect(Collectors.joining());
	}
}

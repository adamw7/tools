package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ClaudeMdConformerTest {

	private final ClaudeMdConformer conformer = new ClaudeMdConformer();

	private List<String> headings(String content) {
		return content.lines().map(String::strip).filter(line -> line.startsWith("#")).toList();
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
		assertTrue(conformed.contains("## Project\n\nSee [AGENTS.md](AGENTS.md)."),
				"the emptied section must be given a body:\n" + conformed);
		ClaudeMdConformer.REQUIRED_SECTIONS.forEach(section -> assertTrue(hasBody(conformed, section),
				section + " must have a body:\n" + conformed));
	}

	@Test
	void keepsTheBodyOfASectionThatAlreadyHasOne() {
		String generated = "# CLAUDE.md\n\n" + requiredSectionsBody();
		String conformed = conformer.conform(generated);
		assertEquals(ClaudeMdConformer.REQUIRED_SECTIONS.size(), conformed.split("Content\\.", -1).length - 1,
				"every original body must survive exactly once:\n" + conformed);
		assertFalse(conformed.contains("Content.\n\nSee [AGENTS.md](AGENTS.md)."),
				"a section that already has a body must not be given a stub");
	}

	/**
	 * Mirrors the enforcer rule's own check: the section must carry something other
	 * than blank lines before the next heading at its level or shallower.
	 */
	private boolean hasBody(String content, String section) {
		List<String> lines = content.lines().map(String::strip).toList();
		int start = lines.indexOf(section);
		return start >= 0 && lines.stream().skip(start + 1L).dropWhile(String::isEmpty).findFirst()
				.filter(line -> !line.startsWith("## ")).isPresent();
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
	void ignoresHeadingsInsideCodeFencesWhenCanonicalising() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				```markdown
				## Project purpose
				```

				## Project

				A repo.

				## Java version

				Java 25.

				## Maven

				Maven.

				## Principles for Java Development

				SOLID.

				## Testing

				JUnit 5.

				## Dependencies

				Existing only.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains("## Project purpose"), "the fenced heading must be left untouched");
		assertTrue(headings(conformed).contains("## Project"));
	}

	@Test
	void leavesAnAlreadyConformingDocumentUnchanged() {
		String conforming = ("# CLAUDE.md\n\n" + ClaudeMdConformer.AGENTS_REFERENCE_LINE + "\n\n"
				+ requiredSectionsBody()).stripTrailing() + "\n";
		String conformed = conformer.conform(conforming);
		assertEquals(conforming, conformed, "a conforming document must be a no-op");
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

	private String requiredSectionsBody() {
		StringBuilder builder = new StringBuilder();
		for (String section : ClaudeMdConformer.REQUIRED_SECTIONS) {
			builder.append(section).append("\n\nContent.\n\n");
		}
		return builder.toString();
	}
}

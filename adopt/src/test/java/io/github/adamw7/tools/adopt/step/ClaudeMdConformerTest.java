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

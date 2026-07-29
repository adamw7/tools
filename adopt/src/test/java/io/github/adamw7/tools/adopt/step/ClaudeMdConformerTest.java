package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

	@Test
	void countsAFencedCodeBlockAsASectionBody() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Project

				```bash
				mvn install
				```

				## Java version

				Java 25.
				""";
		String conformed = conformer.conform(generated);
		assertFalse(conformed.contains("## Project\n\nSee [AGENTS.md](AGENTS.md)."),
				"a section whose body is a code block must not be given a stub:\n" + conformed);
	}

	@Test
	void countsADeeperSubHeadingAsASectionBody() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				## Testing

				### Unit tests

				JUnit 5.
				""";
		String conformed = conformer.conform(generated);
		assertFalse(conformed.contains("## Testing\n\nSee [AGENTS.md](AGENTS.md)."),
				"a section carrying a sub-heading must not be given a stub:\n" + conformed);
	}

	@Test
	void leavesHeadingsInsideTildeFencesAlone() {
		String generated = """
				# CLAUDE.md

				See AGENTS.md.

				~~~markdown
				## Project purpose
				~~~

				## Project

				A repo.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(conformed.contains("## Project purpose"), "the tilde-fenced heading must be left untouched");
		assertTrue(headings(conformed).contains("## Project"));
	}

	@Test
	void addsTheAgentsReferenceWhenTheOnlyMentionIsInsideAFence() {
		String generated = """
				# CLAUDE.md

				```markdown
				See AGENTS.md.
				```

				## Project

				A repo.
				""";
		String conformed = conformer.conform(generated);
		assertTrue(referencesAgentsMdOutsideFences(conformed),
				"a mention that only exists as a code sample does not satisfy the rule:\n" + conformed);
	}

	/** Mirrors how the rule looks for the reference: fenced lines do not count. */
	private boolean referencesAgentsMdOutsideFences(String content) {
		return linesOutsideFences(content).stream()
				.anyMatch(line -> line.contains(ClaudeMdConformer.AGENTS_REFERENCE));
	}

	@Test
	void buildsAWholeSkeletonFromAnEmptyDocument() {
		String conformed = conformer.conform("");
		assertEquals(ClaudeMdConformer.TITLE, conformed.lines().findFirst().orElseThrow());
		assertTrue(headings(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS), conformed);
		ClaudeMdConformer.REQUIRED_SECTIONS.forEach(section -> assertTrue(hasBody(conformed, section),
				section + " must have a body:\n" + conformed));
	}

	@Test
	void collapsesTrailingBlankLinesToASingleNewline() {
		String conforming = ("# CLAUDE.md\n\n" + ClaudeMdConformer.AGENTS_REFERENCE_LINE + "\n\n"
				+ requiredSectionsBody()).stripTrailing() + "\n";
		String conformed = conformer.conform(conforming + "\n\n\n");
		assertEquals(conforming, conformed, "trailing blank lines must be normalised away");
	}

	private String requiredSectionsBody() {
		return ClaudeMdConformer.REQUIRED_SECTIONS.stream()
				.map(section -> section + "\n\nContent.\n\n")
				.collect(Collectors.joining());
	}

	private static final String UNTERMINATED_FENCE = """
			# CLAUDE.md

			Intro.

			```java
			class Foo {}
			""";

	/**
	 * A fence the generated document opened and never closed used to swallow every
	 * appended section: the rule reads fences the same way the conformer does, so it
	 * saw the headings as code and reported all six sections missing, failing the
	 * adoption at its own verification step.
	 */
	@Test
	void closesAnUnterminatedFenceSoAppendedSectionsStayDocumentStructure() {
		String conformed = conformer.conform(UNTERMINATED_FENCE);
		assertTrue(headingsOutsideFences(conformed).containsAll(ClaudeMdConformer.REQUIRED_SECTIONS),
				"every appended section must be a heading, not code:\n" + conformed);
		assertTrue(conformed.contains("class Foo {}"), "the code block keeps its content:\n" + conformed);
	}

	/**
	 * The sections appended below an unterminated fence were invisible to the next
	 * run's own check for them, so re-adopting a repository appended a second — and
	 * then a third — unreachable copy of the whole skeleton.
	 */
	@Test
	void reshapingADocumentWithAnUnterminatedFenceIsIdempotent() {
		String once = conformer.conform(UNTERMINATED_FENCE);
		assertEquals(once, conformer.conform(once), "a second reshape must not append the sections again");
	}

	/** Mirrors how the rule finds headings: a heading inside a fence is code, not structure. */
	private List<String> headingsOutsideFences(String content) {
		return linesOutsideFences(content).stream().filter(line -> line.startsWith("#")).toList();
	}

	/**
	 * The document's lines that sit outside a code fence, stripped. The one place
	 * these tests read fences, so the two questions they ask of a reshaped document —
	 * which headings it carries, and whether it references {@code AGENTS.md} — cannot
	 * come to different answers about what is code.
	 */
	private List<String> linesOutsideFences(String content) {
		List<String> outside = new ArrayList<>();
		boolean fenced = false;
		for (String line : content.lines().map(String::strip).toList()) {
			fenced = collect(outside, line, fenced);
		}
		return outside;
	}

	private boolean collect(List<String> outside, String line, boolean fenced) {
		if (!fenced) {
			outside.add(line);
		}
		return line.startsWith("```") != fenced;
	}
}

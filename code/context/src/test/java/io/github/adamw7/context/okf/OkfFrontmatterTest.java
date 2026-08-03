package io.github.adamw7.context.okf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

public class OkfFrontmatterTest {

	@Test
	void rendersTheOnlyMandatoryField() {
		String rendered = new OkfFrontmatter("Java Source File").render();

		assertTrue(rendered.startsWith("---"));
		assertTrue(rendered.contains("type: \"Java Source File\""));
		assertTrue(rendered.trim().endsWith("---"));
	}

	@Test
	void rejectsAConceptWithoutAType() {
		assertThrows(IllegalArgumentException.class, () -> new OkfFrontmatter(" "));
	}

	@Test
	void rejectsANullType() {
		assertThrows(IllegalArgumentException.class, () -> new OkfFrontmatter(null));
	}

	@Test
	void skipsTheRecommendedFieldsLeftUnset() {
		String rendered = new OkfFrontmatter("Project File").render();

		assertFalse(rendered.contains("title"));
		assertFalse(rendered.contains("description"));
		assertFalse(rendered.contains("resource"));
		assertFalse(rendered.contains("tags"));
		assertFalse(rendered.contains("generated"));
	}

	@Test
	void rendersTheRecommendedFields() {
		String rendered = new OkfFrontmatter("Java Source File")
				.title("A.java")
				.description("Java source file with no project dependencies.")
				.resource("src/A.java")
				.tags(List.of("source", "java"))
				.render();

		assertTrue(rendered.contains("title: \"A.java\""));
		assertTrue(rendered.contains("description: \"Java source file with no project dependencies.\""));
		assertTrue(rendered.contains("resource: \"src/A.java\""));
		assertTrue(rendered.contains("tags: [\"source\", \"java\"]"));
	}

	@Test
	void recordsTheProducingActorAndTheMomentOfProduction() {
		String rendered = new OkfFrontmatter("Java Source File")
				.generated("tools.code.context/1", Instant.parse("2026-08-03T10:15:30Z"))
				.render();

		assertTrue(rendered.contains(
				"generated: { by: \"tools.code.context/1\", at: \"2026-08-03T10:15:30Z\" }"));
	}

	@Test
	void truncatesTheProductionInstantToWholeSeconds() {
		String rendered = new OkfFrontmatter("Java Source File")
				.generated("tools.code.context/1", Instant.parse("2026-08-03T10:15:30.123456Z"))
				.render();

		assertTrue(rendered.contains("at: \"2026-08-03T10:15:30Z\""));
	}

	@Test
	void quotesAValueThatWouldOtherwiseChangeTheMeaningOfTheBlock() {
		String rendered = new OkfFrontmatter("Project File")
				.description("Reads a: b # not a comment")
				.render();

		assertTrue(rendered.contains("description: \"Reads a: b # not a comment\""));
	}

	@Test
	void escapesQuotesAndBackslashes() {
		String rendered = new OkfFrontmatter("Project File").title("a\"b\\c").render();

		assertTrue(rendered.contains("title: \"a\\\"b\\\\c\""));
	}

	@Test
	void tagsAreCopiedSoALaterChangeCannotLeakIn() {
		List<String> tags = new java.util.ArrayList<>(List.of("source"));
		OkfFrontmatter frontmatter = new OkfFrontmatter("Java Source File").tags(tags);
		tags.add("leaked");

		assertFalse(frontmatter.render().contains("leaked"));
	}

	@Test
	void keepsTheFieldsInTheSpecificationsOrder() {
		String rendered = new OkfFrontmatter("Java Source File")
				.title("A.java")
				.description("A description.")
				.resource("src/A.java")
				.tags(List.of("source"))
				.generated("tools.code.context/1", Instant.parse("2026-08-03T10:15:30Z"))
				.render();

		assertEquals(List.of("type", "title", "description", "resource", "tags", "generated"),
				rendered.lines().filter(line -> line.contains(":")).map(line -> line.split(":")[0]).toList());
	}
}

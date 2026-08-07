package io.github.adamw7.context.okf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

public class OkfFrontmatterTest {

	@Test
	void rendersTheOnlyMandatoryField() {
		String rendered = new OkfFrontmatter("Java Source File").render();

		assertTrue(rendered.startsWith("---"));
		assertEquals("Java Source File", fieldsOf(rendered).get("type"));
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

		Map<String, Object> fields = fieldsOf(rendered);
		assertEquals("A.java", fields.get("title"));
		assertEquals("Java source file with no project dependencies.", fields.get("description"));
		assertEquals("src/A.java", fields.get("resource"));
		assertEquals(List.of("source", "java"), fields.get("tags"));
	}

	@Test
	void recordsTheProducingActorAndTheMomentOfProduction() {
		String rendered = new OkfFrontmatter("Java Source File")
				.generated("tools.code.context/1", Instant.parse("2026-08-03T10:15:30Z"))
				.render();

		assertEquals(Map.of("by", "tools.code.context/1", "at", "2026-08-03T10:15:30Z"),
				fieldsOf(rendered).get("generated"));
	}

	@Test
	void truncatesTheProductionInstantToWholeSeconds() {
		String rendered = new OkfFrontmatter("Java Source File")
				.generated("tools.code.context/1", Instant.parse("2026-08-03T10:15:30.123456Z"))
				.render();

		assertEquals("2026-08-03T10:15:30Z", generatedAt(rendered));
	}

	/**
	 * The emitter decides how to quote; what matters is that the block still reads
	 * back as the value it was given, colon, hash, quote, backslash and all. Asserting
	 * the exact quoting instead would pin a choice that is SnakeYAML's to make.
	 */
	@Test
	void survivesValuesThatWouldOtherwiseChangeTheMeaningOfTheBlock() {
		String awkward = "Reads a: b # not a comment, \"quoted\" and back\\slashed";

		String rendered = new OkfFrontmatter("Project File").description(awkward).render();

		assertEquals(awkward, fieldsOf(rendered).get("description"));
	}

	/**
	 * A newline in a title used to be written straight into the middle of a
	 * double-quoted scalar, leaving a block that no longer parsed as YAML. The
	 * break is written as YAML represents one, so the title reads back whole. It is
	 * spelled {@code \n} rather than taken from the platform because that is what a
	 * loader answers on either one.
	 */
	@Test
	void survivesAValueCarryingALineBreak() {
		String rendered = new OkfFrontmatter("Project File").title("first\nsecond").render();

		assertEquals("first\nsecond", fieldsOf(rendered).get("title"));
	}

	@Test
	void tagsAreCopiedSoALaterChangeCannotLeakIn() {
		List<String> tags = new java.util.ArrayList<>(List.of("source"));
		OkfFrontmatter frontmatter = new OkfFrontmatter("Java Source File").tags(tags);
		tags.add("leaked");

		assertFalse(frontmatter.render().contains("leaked"));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> fieldsOf(String rendered) {
		return new Yaml().load(rendered.replace("---", ""));
	}

	@SuppressWarnings("unchecked")
	private static String generatedAt(String rendered) {
		return ((Map<String, String>) fieldsOf(rendered).get("generated")).get("at");
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
				List.copyOf(fieldsOf(rendered).keySet()));
	}
}

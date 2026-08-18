package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class JsonNodesTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void parseObjectReturnsTheRootForAJsonObject() {
		List<String> violations = new ArrayList<>();

		JsonNode root = JsonNodes.parseObject("{\"a\":1}", "settings.json", violations);

		assertNotNull(root);
		assertTrue(root.isObject());
		assertEquals(List.of(), violations);
	}

	@Test
	void parseObjectRejectsAJsonValueThatIsNotAnObject() {
		List<String> violations = new ArrayList<>();

		JsonNode root = JsonNodes.parseObject("[1, 2]", "settings.json", violations);

		assertNull(root);
		assertEquals(1, violations.size());
		assertTrue(violations.get(0).contains("settings.json is not valid JSON: expected a JSON object"),
				violations.toString());
	}

	@Test
	void parseObjectReportsAParseFailureAsAViolation() {
		List<String> violations = new ArrayList<>();

		JsonNode root = JsonNodes.parseObject("{ not json", "settings.json", violations);

		assertNull(root);
		assertEquals(1, violations.size());
		assertTrue(violations.get(0).startsWith("settings.json is not valid JSON:"), violations.toString());
	}

	@Test
	void parseObjectRejectsBlankContentThatParsesToNoNode() {
		List<String> violations = new ArrayList<>();

		JsonNode root = JsonNodes.parseObject("   ", "settings.json", violations);

		assertNull(root);
		assertEquals(1, violations.size());
		assertTrue(violations.get(0).contains("expected a JSON object"), violations.toString());
	}

	@Test
	void objectAtKeyReturnsTheChildObject() {
		JsonNode node = parse("{\"hooks\":{\"x\":1}}");

		JsonNode child = JsonNodes.objectAt(node, "hooks");

		assertNotNull(child);
		assertEquals(1, child.get("x").asInt());
	}

	@Test
	void objectAtKeyReturnsNullWhenTheKeyIsAbsent() {
		assertNull(JsonNodes.objectAt(parse("{}"), "hooks"));
	}

	@Test
	void objectAtKeyReturnsNullWhenTheChildIsNotAnObject() {
		assertNull(JsonNodes.objectAt(parse("{\"hooks\":[]}"), "hooks"));
	}

	@Test
	void arrayAtKeyReturnsTheChildArray() {
		JsonNode array = JsonNodes.arrayAt(parse("{\"list\":[1,2,3]}"), "list");

		assertNotNull(array);
		assertEquals(3, array.size());
	}

	@Test
	void arrayAtKeyReturnsNullWhenTheKeyIsAbsent() {
		assertNull(JsonNodes.arrayAt(parse("{}"), "list"));
	}

	@Test
	void arrayAtKeyReturnsNullWhenTheChildIsNotAnArray() {
		assertNull(JsonNodes.arrayAt(parse("{\"list\":{}}"), "list"));
	}

	@Test
	void objectAtIndexReturnsTheObjectElement() {
		JsonNode element = JsonNodes.objectAt(parse("[{\"k\":\"v\"}]"), 0);

		assertNotNull(element);
		assertEquals("v", element.get("k").asText());
	}

	@Test
	void objectAtIndexReturnsNullWhenTheElementIsNotAnObject() {
		assertNull(JsonNodes.objectAt(parse("[42]"), 0));
	}

	@Test
	void objectAtIndexReturnsNullWhenTheIndexIsOutOfBounds() {
		assertNull(JsonNodes.objectAt(parse("[]"), 0));
	}

	@Test
	void textAtReturnsTheTextualValue() {
		assertEquals("command", JsonNodes.textAt(parse("{\"type\":\"command\"}"), "type", "fallback"));
	}

	@Test
	void textAtReturnsTheDefaultWhenTheKeyIsAbsent() {
		assertEquals("fallback", JsonNodes.textAt(parse("{}"), "type", "fallback"));
	}

	@Test
	void textAtReturnsTheDefaultWhenTheValueIsJsonNull() {
		assertEquals("fallback", JsonNodes.textAt(parse("{\"type\":null}"), "type", "fallback"));
	}

	@Test
	void fieldNamesPreserveDocumentOrder() {
		assertEquals(List.of("b", "a", "c"), JsonNodes.fieldNames(parse("{\"b\":1,\"a\":2,\"c\":3}")));
	}

	@Test
	void fieldNamesAreEmptyForAnObjectWithoutFields() {
		assertEquals(List.of(), JsonNodes.fieldNames(parse("{}")));
	}

	@Test
	void parseObjectRejectsContentAfterTheClosingBrace() {
		List<String> violations = new ArrayList<>();

		// Jackson stops at the first complete value by default, so this used to
		// parse clean and the trailing text was never seen.
		JsonNode root = JsonNodes.parseObject("{\"a\":1} and then some", "settings.json", violations);

		assertNull(root);
		assertEquals(1, violations.size());
		assertTrue(violations.get(0).startsWith("settings.json is not valid JSON:"), violations.toString());
	}

	@Test
	void parseObjectRejectsADuplicatedKey() {
		List<String> violations = new ArrayList<>();

		// The second definition silently won before, which is exactly the kind of
		// shadowing these rules exist to catch.
		JsonNode root = JsonNodes.parseObject("{\"a\":{\"x\":1},\"a\":{\"x\":2}}", "settings.json", violations);

		assertNull(root);
		assertEquals(1, violations.size());
		assertTrue(violations.get(0).contains("Duplicate field 'a'"), violations.toString());
	}

	@Test
	void readsNoTextFromAValueThatIsNotAString() {
		JsonNode node = parse("{ \"number\": 123, \"flag\": true, \"list\": [\"a\"], \"object\": {} }");

		// asText() coerced every one of these, so a JSON 123 answered for text
		// nobody wrote and the malformed configuration passed unreported.
		assertEquals("fallback", JsonNodes.textAt(node, "number", "fallback"));
		assertEquals("fallback", JsonNodes.textAt(node, "flag", "fallback"));
		assertEquals("fallback", JsonNodes.textAt(node, "list", "fallback"));
		assertEquals("fallback", JsonNodes.textAt(node, "object", "fallback"));
	}

	@Test
	void readsTheTextOfAStringValue() {
		assertEquals("npx", JsonNodes.textAt(parse("{ \"command\": \"npx\" }"), "command", "fallback"));
	}

	@Test
	void tellsADeclaredNonStringFromAnAbsentKey() {
		JsonNode node = parse("{ \"number\": 123, \"text\": \"x\", \"nothing\": null }");

		assertTrue(JsonNodes.declaresNonText(node, "number"));
		assertFalse(JsonNodes.declaresNonText(node, "text"));
		assertFalse(JsonNodes.declaresNonText(node, "nothing"));
		assertFalse(JsonNodes.declaresNonText(node, "absent"));
	}

	private static JsonNode parse(String json) {
		try {
			return MAPPER.readTree(json);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("test fixture is not valid JSON: " + json, e);
		}
	}
}

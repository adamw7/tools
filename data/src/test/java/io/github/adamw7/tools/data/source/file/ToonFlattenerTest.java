package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Drives the {@link ToonFlattener} grammar directly, line by line, capturing every
 * {@code key/value} pair it pushes to the sink. Unlike the data-source tests, which sample the
 * flattened output through a scanner, these assertions pin the exact pairs and their emission
 * order, including the trailing array-count pair a nested array block emits only when it closes.
 */
public class ToonFlattenerTest {

	private final List<Map.Entry<String, String>> pairs = new ArrayList<>();
	private final ToonFlattener flattener = new ToonFlattener((key, value) -> pairs.add(new SimpleEntry<>(key, value)));

	private void feed(String toon) {
		for (String line : toon.split("\n", -1)) {
			flattener.accept(line);
		}
		flattener.finish();
	}

	private Map<String, String> asMap() {
		Map<String, String> map = new LinkedHashMap<>();
		for (Map.Entry<String, String> pair : pairs) {
			map.put(pair.getKey(), pair.getValue());
		}
		return map;
	}

	private void assertPair(String key, String value) {
		assertEquals(value, asMap().get(key), () -> "expected " + key + "=" + value + " but pairs were " + pairs);
	}

	@Test
	public void emitsSimpleKeyValuePair() {
		feed("appName: MyApp");
		assertPair("appName", "MyApp");
		assertEquals(1, pairs.size());
	}

	@Test
	public void blankAndWhitespaceOnlyLinesAreIgnored() {
		feed("a: 1\n\n   \nb: 2");
		assertPair("a", "1");
		assertPair("b", "2");
		assertEquals(2, pairs.size());
	}

	@Test
	public void unquotesKeyValueContent() {
		feed("message: \"Hello, World!\"");
		assertPair("message", "Hello, World!");
	}

	@Test
	public void emptyValueOpensNestedObjectFrame() {
		feed("context:\n  task: hike\n  location: Boulder");
		assertPair("context.task", "hike");
		assertPair("context.location", "Boulder");
		assertEquals(2, pairs.size());
	}

	@Test
	public void nestedObjectFramesPrefixDeeply() {
		feed("level1:\n  level2:\n    level3:\n      value: deep");
		assertPair("level1.level2.level3.value", "deep");
	}

	@Test
	public void siblingKeyPopsBackToOuterFrame() {
		feed("outer:\n  inner: 1\nsibling: 2");
		assertPair("outer.inner", "1");
		assertPair("sibling", "2");
	}

	@Test
	public void inlinePrimitiveArrayEmitsCountThenElements() {
		feed("tags[3]: admin,ops,dev");
		assertEquals(List.of(
				new SimpleEntry<>("tags", "3"),
				new SimpleEntry<>("tags[0]", "admin"),
				new SimpleEntry<>("tags[1]", "ops"),
				new SimpleEntry<>("tags[2]", "dev")), pairs);
	}

	@Test
	public void inlinePrimitiveArrayHonoursQuotedComma() {
		feed("tags[2]: \"a,b\",c");
		assertPair("tags", "2");
		assertPair("tags[0]", "a,b");
		assertPair("tags[1]", "c");
	}

	@Test
	public void tabularArrayEmitsHeaderThenRows() {
		feed("users[2]{id,name,role}:\n  1,Alice,admin\n  2,Bob,user");
		Map<String, String> map = asMap();
		assertEquals("2", map.get("users"));
		assertEquals("1", map.get("users[0].id"));
		assertEquals("Alice", map.get("users[0].name"));
		assertEquals("admin", map.get("users[0].role"));
		assertEquals("2", map.get("users[1].id"));
		assertEquals("Bob", map.get("users[1].name"));
		assertEquals("user", map.get("users[1].role"));
	}

	@Test
	public void tabularArrayAcceptsInlineFirstRowOnHeaderLine() {
		feed("users[1]{id,name}: 7,Alice");
		assertPair("users[0].id", "7");
		assertPair("users[0].name", "Alice");
	}

	@Test
	public void tabularArrayStopsAtDeclaredCountAndResumesDocument() {
		feed("users[1]{id}:\n  1\n  2\nname: bob");
		assertPair("users[0].id", "1");
		// The second row is beyond the declared count, so it is not a users[1] entry.
		assertFalse(asMap().containsKey("users[1].id"));
		assertPair("name", "bob");
	}

	@Test
	public void tabularArrayStopsAtTopLevelSectionBeforeCount() {
		feed("users[3]{id}:\n  1\nname: bob");
		assertPair("users[0].id", "1");
		assertFalse(asMap().containsKey("users[1].id"));
		assertPair("name", "bob");
	}

	@Test
	public void nestedArrayEmitsItemsThenTrailingCount() {
		feed("priorities[3]:\n  - high\n  - medium\n  - low");
		assertEquals(List.of(
				new SimpleEntry<>("priorities[0]", "high"),
				new SimpleEntry<>("priorities[1]", "medium"),
				new SimpleEntry<>("priorities[2]", "low"),
				new SimpleEntry<>("priorities", "3")), pairs);
	}

	@Test
	public void nestedArrayCountUsesDeclaredSizeEvenWhenFinishClosesEarly() {
		feed("items[3]:\n  - only");
		assertPair("items[0]", "only");
		// finish() closes the still-open block, emitting the declared count of 3.
		assertPair("items", "3");
		assertFalse(asMap().containsKey("items[1]"));
	}

	@Test
	public void objectFollowedByTabularArrayKeepsBothFlattened() {
		feed("db:\n  host: localhost\nrows[1]{id}:\n  9");
		assertPair("db.host", "localhost");
		assertPair("rows[0].id", "9");
	}

	@Test
	public void nonMatchingLineEmitsNothing() {
		feed("- stray item without a header");
		assertTrue(pairs.isEmpty());
	}
}

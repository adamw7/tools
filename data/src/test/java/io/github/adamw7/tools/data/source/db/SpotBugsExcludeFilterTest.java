package io.github.adamw7.tools.data.source.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Pins the scope of the module's SpotBugs exclude filter. The two JDBC sources run the
 * caller's query on purpose, so {@code SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE} is
 * accepted there — but only there; the forward-only read methods answer {@code null} for
 * "no row" on purpose, so {@code PZLA_PREFER_ZERO_LENGTH_ARRAYS} is accepted for those
 * four methods — and only those. A filter that grew a Match without a class, a method or
 * a pattern would silently stop either detector reporting a genuine finding elsewhere in
 * the module, which is what these assertions exist to catch.
 */
public class SpotBugsExcludeFilterTest {

	private static final Path FILTER = Path.of("spotbugs-exclude.xml");
	private static final String SQL_INJECTION = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE";
	private static final String NULL_ARRAY = "PZLA_PREFER_ZERO_LENGTH_ARRAYS";
	private static final String SOURCE_FILE_PACKAGE = "io.github.adamw7.tools.data.source.file.";

	private static List<Element> matches;

	@BeforeAll
	static void readFilter() throws IOException, ParserConfigurationException, SAXException {
		Document document = parse(Files.readString(FILTER));
		assertEquals("FindBugsFilter", document.getDocumentElement().getNodeName());
		matches = elementsOf(document.getElementsByTagName("Match"));
	}

	private static Document parse(String xml) throws ParserConfigurationException, IOException, SAXException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
	}

	private static List<Element> elementsOf(NodeList nodes) {
		List<Element> elements = new ArrayList<>();
		for (int i = 0; i < nodes.getLength(); ++i) {
			elements.add((Element) nodes.item(i));
		}
		return elements;
	}

	@Test
	public void everyExclusionNamesAClassAMethodAndAPattern() {
		assertFalse(matches.isEmpty(), "the filter is wired into the build, so it must exclude something");
		for (Element match : matches) {
			assertEquals(1, match.getElementsByTagName("Class").getLength(), "one Class per Match: " + describe(match));
			assertEquals(1, match.getElementsByTagName("Method").getLength(), "one Method per Match: " + describe(match));
			assertEquals(1, match.getElementsByTagName("Bug").getLength(), "one Bug per Match: " + describe(match));
		}
	}

	@Test
	public void onlyTheDecidedMethodsAreExcluded() {
		List<String> excluded = matches.stream().map(SpotBugsExcludeFilterTest::describe).toList();
		assertEquals(List.of(
				IterableSQLDataSource.class.getName() + "#executeQuery:" + SQL_INJECTION,
				InMemorySQLDataSource.class.getName() + "#readAll:" + SQL_INJECTION,
				IterableSQLDataSource.class.getName() + "#readRow:" + NULL_ARRAY,
				SOURCE_FILE_PACKAGE + "AbstractInMemoryMapDataSource#nextRow:" + NULL_ARRAY,
				SOURCE_FILE_PACKAGE + "AbstractIterableJacksonDataSource#consume:" + NULL_ARRAY,
				SOURCE_FILE_PACKAGE + "CSVDataSource#nextRow:" + NULL_ARRAY),
				excluded);
	}

	private static String describe(Element match) {
		return attribute(match, "Class", "name") + "#" + attribute(match, "Method", "name") + ":"
				+ attribute(match, "Bug", "pattern");
	}

	private static String attribute(Element match, String tag, String name) {
		NodeList tagged = match.getElementsByTagName(tag);
		return tagged.getLength() == 0 ? "" : ((Element) tagged.item(0)).getAttribute(name);
	}
}

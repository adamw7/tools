package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class HtmlReportTest {

	@TempDir
	private Path tempDir;

	@Test
	void rendersHeaderViolationsAndHowToFixWhenThereAreViolations() {
		String html = new HtmlReport("CLAUDE.md is not well formed:",
				List.of("missing section: ## Build"),
				List.of("Add the ## Build section.")).render();

		assertTrue(html.contains("Check failed"), html);
		assertTrue(html.contains("What failed and why"), html);
		assertTrue(html.contains("CLAUDE.md is not well formed:"), html);
		assertTrue(html.contains("missing section: ## Build"), html);
		assertTrue(html.contains("How to fix"), html);
		assertTrue(html.contains("Add the ## Build section."), html);
	}

	@Test
	void rendersViolationsAndHowToFixAsColumnsOfOneTable() {
		String html = new HtmlReport("CLAUDE.md is not well formed:",
				List.of("missing section: ## Build"),
				List.of("Add the ## Build section.")).render();

		assertTrue(html.contains("<table class=\"report\">"), html);
		assertFalse(html.contains("<table class=\"how-to-fix\">"), html);
		assertTrue(html.contains("<th>What failed and why</th>"), html);
		assertTrue(html.contains("<th>How to fix</th>"), html);
		assertTrue(html.contains("<td class=\"failure\">missing section: ## Build</td>"), html);
		assertTrue(html.contains("<td class=\"fix\">Add the ## Build section.</td>"), html);
	}

	@Test
	void padsTheShorterColumnWhenViolationsAndFixesDiffer() {
		String html = new HtmlReport("CLAUDE.md is not well formed:",
				List.of("missing section: ## Build", "missing section: ## Test"),
				List.of("Add the missing sections.")).render();

		assertTrue(html.contains("<td class=\"failure\">missing section: ## Test</td>"), html);
		assertTrue(html.contains("<td class=\"fix\"></td>"), html);
	}

	@Test
	void rendersAPassedPageWithNoHowToFixWhenThereAreNoViolations() {
		String html = new HtmlReport("CLAUDE.md is not well formed:",
				List.of(),
				List.of("Add the ## Build section.")).render();

		assertTrue(html.contains("Check passed"), html);
		assertTrue(html.contains("No violations were found."), html);
		assertFalse(html.contains("How to fix"), html);
	}

	@Test
	void escapesMarkupInRuleSuppliedText() {
		String html = new HtmlReport("<header> & \"stuff\"",
				List.of("token <script> must not appear"),
				List.of("Remove <script> & re-run.")).render();

		assertFalse(html.contains("<script>"), html);
		assertTrue(html.contains("&lt;script&gt;"), html);
		assertTrue(html.contains("&amp;"), html);
		assertTrue(html.contains("&quot;stuff&quot;"), html);
	}

	@Test
	void writesTheRenderedReportToDisk() throws Exception {
		File report = tempDir.resolve("report.html").toFile();

		new HtmlReport("header", List.of("boom"), List.of("fix it")).writeTo(report);

		String written = Files.readString(report.toPath());
		assertTrue(written.contains("boom"), written);
		assertTrue(written.contains("fix it"), written);
		assertTrue(written.startsWith("<!DOCTYPE html>"), written);
	}

	@Test
	void failsWhenTheReportCannotBeWritten() {
		File unwritable = tempDir.resolve("missing-dir").resolve("report.html").toFile();

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				() -> new HtmlReport("header", List.of("boom"), List.of("fix it")).writeTo(unwritable));
		assertTrue(exception.getMessage().contains("Could not write HTML report"), exception.getMessage());
	}

	@Test
	void producesWellFormedMarkupForTheFailurePage() throws Exception {
		String html = new HtmlReport("CLAUDE.md is not well formed:",
				List.of("missing section: ## Build"),
				List.of("Add the ## Build section.")).render();

		Document document = parse(html);

		assertEquals("html", document.getDocumentElement().getTagName());
		Element table = firstElementByTag(document, "table");
		assertEquals(List.of("#", "What failed and why", "How to fix"), headerCells(table));
	}

	@Test
	void producesWellFormedMarkupForThePassedPage() throws Exception {
		String html = new HtmlReport("CLAUDE.md is not well formed:", List.of(),
				List.of("Add the ## Build section.")).render();

		Document document = parse(html);

		assertEquals("html", document.getDocumentElement().getTagName());
		assertEquals(0, document.getElementsByTagName("table").getLength());
	}

	@Test
	void keepsMarkupWellFormedWhenRuleTextContainsSpecialCharacters() throws Exception {
		String html = new HtmlReport("<header> & \"stuff\"",
				List.of("token <script> must not appear"),
				List.of("Remove <script> & re-run.")).render();

		Document document = parse(html);

		assertEquals("html", document.getDocumentElement().getTagName());
	}

	/** Parses the rendered report as XML, which fails the test if the markup is not well formed. */
	private Document parse(String html) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
	}

	private Element firstElementByTag(Document document, String tag) {
		return (Element) document.getElementsByTagName(tag).item(0);
	}

	private List<String> headerCells(Element table) {
		List<String> headers = new ArrayList<>();
		NodeList cells = table.getElementsByTagName("th");
		for (int index = 0; index < cells.getLength(); index++) {
			Node cell = cells.item(index);
			headers.add(cell.getTextContent());
		}
		return headers;
	}
}

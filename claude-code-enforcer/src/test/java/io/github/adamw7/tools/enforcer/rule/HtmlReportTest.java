package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportTest {

	@TempDir
	private Path tempDir;

	@Test
	void rendersHeaderViolationsAndHowToFixWhenThereAreViolations() {
		Document document = Jsoup.parse(new HtmlReport("CLAUDE.md is not well formed:",
				List.of("missing section: ## Build"),
				List.of("Add the ## Build section.")).render());

		assertEquals("Check failed", document.selectFirst("p.status").text());
		assertEquals("CLAUDE.md is not well formed:", document.selectFirst("section p").text());
		Element row = document.selectFirst("table.report tbody tr");
		assertEquals("missing section: ## Build", row.selectFirst("td.failure").text());
		assertEquals("Add the ## Build section.", row.selectFirst("td.fix").text());
	}

	@Test
	void rendersViolationsAndHowToFixAsColumnsOfOneTable() {
		Document document = Jsoup.parse(new HtmlReport("CLAUDE.md is not well formed:",
				List.of("missing section: ## Build"),
				List.of("Add the ## Build section.")).render());

		assertEquals(1, document.select("table").size());
		assertEquals(List.of("#", "What failed and why", "How to fix"),
				document.select("table.report thead th").eachText());
	}

	@Test
	void padsTheShorterColumnWhenViolationsAndFixesDiffer() {
		Document document = Jsoup.parse(new HtmlReport("CLAUDE.md is not well formed:",
				List.of("missing section: ## Build", "missing section: ## Test"),
				List.of("Add the missing sections.")).render());

		Elements rows = document.select("table.report tbody tr");
		assertEquals(2, rows.size());
		assertEquals("missing section: ## Test", rows.get(1).selectFirst("td.failure").text());
		assertEquals("", rows.get(1).selectFirst("td.fix").text());
	}

	@Test
	void rendersAPassedPageWithNoHowToFixWhenThereAreNoViolations() {
		Document document = Jsoup.parse(new HtmlReport("CLAUDE.md is not well formed:",
				List.of(),
				List.of("Add the ## Build section.")).render());

		assertEquals("Check passed", document.selectFirst("p.status").text());
		assertTrue(document.select("table").isEmpty(), document.html());
		assertTrue(document.body().text().contains("No violations were found."), document.html());
	}

	@Test
	void escapesMarkupInRuleSuppliedText() {
		Document document = Jsoup.parse(new HtmlReport("<header> & \"stuff\"",
				List.of("token <script> must not appear"),
				List.of("Remove <script> & re-run.")).render());

		assertTrue(document.select("script").isEmpty(), document.html());
		assertEquals("token <script> must not appear",
				document.selectFirst("td.failure").text());
		assertEquals("Remove <script> & re-run.", document.selectFirst("td.fix").text());
	}

	@Test
	void producesValidMarkupWithoutParseErrors() {
		assertNoParseErrors(new HtmlReport("CLAUDE.md is not well formed:",
				List.of("missing section: ## Build"),
				List.of("Add the ## Build section.")).render());
		assertNoParseErrors(new HtmlReport("<header> & \"stuff\"",
				List.of("token <script> must not appear"),
				List.of("Remove <script> & re-run.")).render());
		assertNoParseErrors(new HtmlReport("CLAUDE.md is not well formed:", List.of(),
				List.of("Add the ## Build section.")).render());
	}

	@Test
	void writesTheRenderedReportToDisk() throws Exception {
		File report = tempDir.resolve("report.html").toFile();

		new HtmlReport("header", List.of("boom"), List.of("fix it")).writeTo(report);

		String written = Files.readString(report.toPath());
		Document document = Jsoup.parse(written);
		assertTrue(written.startsWith("<!DOCTYPE html>"), written);
		assertEquals("boom", document.selectFirst("td.failure").text());
		assertEquals("fix it", document.selectFirst("td.fix").text());
	}

	@Test
	void createsMissingParentDirectories() throws Exception {
		// A report under target/ is typically written at validate, before any
		// plugin has created that directory, so the writer must create it.
		File report = tempDir.resolve("missing-dir").resolve("nested").resolve("report.html").toFile();

		new HtmlReport("header", List.of("boom"), List.of("fix it")).writeTo(report);

		assertTrue(Files.readString(report.toPath()).contains("boom"));
	}

	@Test
	void failsWhenTheReportCannotBeWritten() throws Exception {
		Files.writeString(tempDir.resolve("blocker"), "a file, not a directory");
		File unwritable = tempDir.resolve("blocker").resolve("report.html").toFile();

		EnforcerRuleException exception = assertThrows(EnforcerRuleException.class,
				() -> new HtmlReport("header", List.of("boom"), List.of("fix it")).writeTo(unwritable));
		assertTrue(exception.getMessage().contains("Could not write HTML report"), exception.getMessage());
	}

	/** Parses with the HTML5 parser in error-tracking mode and fails on any reported problem. */
	private void assertNoParseErrors(String html) {
		Parser parser = Parser.htmlParser().setTrackErrors(10);
		parser.parseInput(html, "");
		assertTrue(parser.getErrors().isEmpty(), parser.getErrors().toString());
	}
}

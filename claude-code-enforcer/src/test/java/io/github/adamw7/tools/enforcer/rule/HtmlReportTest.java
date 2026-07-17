package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
	void rendersViolationsAndHowToFixAsHtmlTables() {
		String html = new HtmlReport("CLAUDE.md is not well formed:",
				List.of("missing section: ## Build"),
				List.of("Add the ## Build section.")).render();

		assertTrue(html.contains("<table class=\"violations\">"), html);
		assertTrue(html.contains("<th>What failed and why</th>"), html);
		assertTrue(html.contains("<table class=\"how-to-fix\">"), html);
		assertTrue(html.contains("<td>missing section: ## Build</td>"), html);
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
}

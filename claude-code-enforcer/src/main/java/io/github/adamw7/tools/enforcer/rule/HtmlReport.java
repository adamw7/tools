package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Renders a self-contained HTML report of a rule's outcome and writes it to a
 * file. The page states what failed and why — a numbered table with the header
 * plus one row per collected violation — and how to fix it — a numbered table of
 * the remediation steps the rule supplies. When there are no violations it
 * renders a short "passed" page, so a configured report file always reflects the
 * latest run rather than leaving a stale failure behind.
 * <p>
 * The document is inlined (styles included, no external assets) so it opens
 * anywhere, and every piece of rule-supplied text is HTML-escaped so a violation
 * message containing {@code <} or {@code &} cannot break or inject markup.
 */
final class HtmlReport {

	private final String header;
	private final List<String> violations;
	private final List<String> howToFix;

	HtmlReport(String header, List<String> violations, List<String> howToFix) {
		this.header = header;
		this.violations = violations;
		this.howToFix = howToFix;
	}

	/** Writes the rendered report to {@code file}, failing the build if it cannot be written. */
	void writeTo(File file) throws EnforcerRuleException {
		try {
			Files.writeString(file.toPath(), render(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new EnforcerRuleException("Could not write HTML report to " + file, e);
		}
	}

	String render() {
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>\n");
		html.append("<html lang=\"en\">\n<head>\n");
		html.append("<meta charset=\"utf-8\">\n");
		html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
		html.append("<title>Claude Code Enforcer report</title>\n");
		html.append("<style>").append(css()).append("</style>\n");
		html.append("</head>\n<body>\n");
		html.append("<main>\n");
		appendHeading(html);
		if (violations.isEmpty()) {
			appendPassed(html);
		} else {
			appendFailures(html);
			appendHowToFix(html);
		}
		html.append("</main>\n</body>\n</html>\n");
		return html.toString();
	}

	private void appendHeading(StringBuilder html) {
		String status = violations.isEmpty() ? "passed" : "failed";
		html.append("<h1>Claude Code Enforcer report</h1>\n");
		html.append("<p class=\"status ").append(status).append("\">Check ").append(status).append("</p>\n");
	}

	private void appendPassed(StringBuilder html) {
		html.append("<p>No violations were found.</p>\n");
	}

	private void appendFailures(StringBuilder html) {
		html.append("<section>\n<h2>What failed and why</h2>\n");
		html.append("<p>").append(escape(header)).append("</p>\n");
		html.append("<table class=\"violations\">\n");
		html.append("<thead>\n<tr><th class=\"num\">#</th><th>What failed and why</th></tr>\n</thead>\n");
		html.append("<tbody>\n");
		appendNumberedRows(html, violations);
		html.append("</tbody>\n</table>\n</section>\n");
	}

	private void appendHowToFix(StringBuilder html) {
		if (howToFix.isEmpty()) {
			return;
		}
		html.append("<section>\n<h2>How to fix</h2>\n");
		html.append("<table class=\"how-to-fix\">\n");
		html.append("<thead>\n<tr><th class=\"num\">Step</th><th>Action</th></tr>\n</thead>\n");
		html.append("<tbody>\n");
		appendNumberedRows(html, howToFix);
		html.append("</tbody>\n</table>\n</section>\n");
	}

	private void appendNumberedRows(StringBuilder html, List<String> cells) {
		int number = 1;
		for (String cell : cells) {
			html.append("<tr><td class=\"num\">").append(number).append("</td><td>")
					.append(escape(cell)).append("</td></tr>\n");
			number++;
		}
	}

	private String css() {
		return "body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;"
				+ "line-height:1.5;color:#1a1a1a;background:#f6f7f9;margin:0;padding:2rem;}"
				+ "main{max-width:52rem;margin:0 auto;background:#fff;padding:1.5rem 2rem;"
				+ "border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.1);}"
				+ "h1{font-size:1.5rem;margin:0 0 .5rem;}h2{font-size:1.15rem;margin:1.5rem 0 .5rem;}"
				+ ".status{display:inline-block;font-weight:600;padding:.25rem .75rem;border-radius:999px;}"
				+ ".status.failed{background:#fdecea;color:#b3261e;}"
				+ ".status.passed{background:#e6f4ea;color:#1e7e34;}"
				+ "table{border-collapse:collapse;width:100%;margin:.25rem 0;}"
				+ "th,td{border:1px solid #e0e0e0;padding:.5rem .75rem;text-align:left;vertical-align:top;}"
				+ "thead th{background:#f0f1f3;font-weight:600;}"
				+ "td.num,th.num{width:3rem;text-align:right;color:#666;white-space:nowrap;}"
				+ ".violations td{color:#b3261e;}"
				+ ".violations td.num{color:#666;}";
	}

	/** Escapes the five characters that are significant in HTML text and attributes. */
	private String escape(String text) {
		return text.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}
}

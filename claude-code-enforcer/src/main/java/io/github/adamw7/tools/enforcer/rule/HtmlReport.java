package io.github.adamw7.tools.enforcer.rule;

import static io.github.adamw7.tools.enforcer.rule.HtmlPage.escape;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Renders a self-contained HTML report of a rule's outcome and writes it to a
 * file. When there are violations the page shows a single numbered table pairing a
 * "What failed and why" column (one collected violation per row) with a "How to
 * fix" column (the remediation steps the rule supplies); the two lists are
 * independent, so rows run to the longer of the two. When there are none it
 * renders a short "passed" page, so a configured report file never leaves a stale
 * failure behind.
 * <p>
 * The frame, the styling and the escaping that keeps a violation message
 * containing {@code <} or {@code &} from breaking or injecting markup are
 * {@link HtmlPage}'s.
 */
final class HtmlReport {

	private static final String TITLE = "Claude Code Enforcer report";

	private static final String CSS = "h2{font-size:1.15rem;margin:1.5rem 0 .5rem;}"
			+ "table{margin:.25rem 0;}th,td{vertical-align:top;}"
			+ "td.num,th.num{width:3rem;text-align:right;color:#666;white-space:nowrap;}"
			+ ".report td.failure{color:#b3261e;}"
			+ ".report td.fix{color:#1a1a1a;}";

	private final String header;
	private final List<String> violations;
	private final List<String> howToFix;

	HtmlReport(String header, List<String> violations, List<String> howToFix) {
		this.header = header;
		this.violations = violations;
		this.howToFix = howToFix;
	}

	/**
	 * Writes the rendered report to {@code file}, failing the build if it cannot be
	 * written. Missing parent directories are created first: a report under
	 * {@code target/} is written at {@code validate}, before any plugin created it. A
	 * path with no parent to create — which {@link Path#getParent} answers {@code null}
	 * for at a filesystem root — simply has nothing to make before the write.
	 */
	void writeTo(File file) throws EnforcerRuleException {
		try {
			Path path = file.toPath().toAbsolutePath();
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(path, render(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new EnforcerRuleException("Could not write HTML report to " + file, e);
		}
	}

	String render() {
		HtmlPage page = new HtmlPage(TITLE, CSS);
		StringBuilder html = page.body();
		appendStatus(html);
		if (violations.isEmpty()) {
			html.append("<p>No violations were found.</p>\n");
		} else {
			appendFailures(html);
		}
		return page.render();
	}

	private void appendStatus(StringBuilder html) {
		String status = violations.isEmpty() ? "passed" : "failed";
		html.append("<p class=\"status ").append(status).append("\">Check ").append(status).append("</p>\n");
	}

	private void appendFailures(StringBuilder html) {
		html.append("<section>\n<h2>What failed and how to fix it</h2>\n");
		html.append("<p>").append(escape(header)).append("</p>\n");
		html.append("<table class=\"report\">\n");
		html.append("<thead>\n<tr><th class=\"num\">#</th><th>What failed and why</th>"
				+ "<th>How to fix</th></tr>\n</thead>\n");
		html.append("<tbody>\n");
		appendCombinedRows(html);
		html.append("</tbody>\n</table>\n</section>\n");
	}

	private void appendCombinedRows(StringBuilder html) {
		int rows = Math.max(violations.size(), howToFix.size());
		for (int index = 0; index < rows; index++) {
			html.append("<tr><td class=\"num\">").append(index + 1).append("</td>")
					.append("<td class=\"failure\">").append(cell(violations, index)).append("</td>")
					.append("<td class=\"fix\">").append(cell(howToFix, index)).append("</td></tr>\n");
		}
	}

	private String cell(List<String> cells, int index) {
		return index < cells.size() ? escape(cells.get(index)) : "";
	}
}

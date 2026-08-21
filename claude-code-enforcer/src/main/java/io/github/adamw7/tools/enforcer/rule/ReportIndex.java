package io.github.adamw7.tools.enforcer.rule;

import static io.github.adamw7.tools.enforcer.rule.HtmlPage.escape;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The one page a build's reports are read from: an {@code index.html} beside the
 * per-rule reports, listing every rule that has reported into the directory and
 * whether it passed.
 *
 * <p>Twenty rules writing twenty HTML files is twenty files nobody opens. Each
 * still writes its own, where its violations and remediation steps are, but the
 * index says which are worth opening — the question a reader arrives with.
 *
 * <p>A rule reports as it runs and knows nothing of what the others found, so the
 * index is rebuilt from a sidecar of outcomes rather than assembled at the end.
 * A build running a subset of the rules indexes that subset, and re-running one
 * rule updates its row rather than duplicating it.
 *
 * <p>Failing to write the index never fails the build: the verdict is decided and
 * the rule's own report written, so losing the page that links them is reported as
 * a warning and the run continues.
 */
final class ReportIndex {

	static final String INDEX_FILE = "index.html";

	/**
	 * The recorded outcomes the page is rebuilt from. Not itself HTML: the page is
	 * regenerated whole on every write, and re-parsing markup is a worse way to keep
	 * a list of pairs.
	 */
	static final String SIDECAR_FILE = ".claude-code-enforcer-index";

	private static final String SEPARATOR = "\t";

	private static final String TITLE = "Claude Code Enforcer reports";

	private static final String CSS = "table{margin:1rem 0;}"
			+ "td.ok{color:#1e7e34;}td.bad{color:#b3261e;font-weight:600;}";

	private ReportIndex() {
	}

	/**
	 * Records one rule's outcome and rebuilds the index around it.
	 *
	 * @param directory  the report directory the rule wrote its own report into
	 * @param ruleName   the rule's configured name, which is also its report's file name
	 * @param violations how many un-suppressed violations it reported
	 * @throws IOException when the sidecar or the page cannot be written, which the
	 *                     caller downgrades to a warning
	 */
	static synchronized void record(File directory, String ruleName, int violations) throws IOException {
		Path path = directory.toPath().toAbsolutePath();
		Files.createDirectories(path);
		Map<String, Integer> outcomes = read(path.resolve(SIDECAR_FILE));
		outcomes.put(ruleName, violations);
		write(path.resolve(SIDECAR_FILE), outcomes);
		Files.writeString(path.resolve(INDEX_FILE), render(outcomes), StandardCharsets.UTF_8);
	}

	/**
	 * The outcomes recorded so far, sorted by rule name so the page's order is the
	 * build's to read rather than the order the reactor happened to run the rules in.
	 * A sidecar line that is not a name and a count is dropped: the file lives in a
	 * build directory anyone may write to, and a rule's verdict must not depend on
	 * what it found there.
	 */
	private static Map<String, Integer> read(Path sidecar) throws IOException {
		Map<String, Integer> outcomes = new TreeMap<>();
		if (!Files.isRegularFile(sidecar)) {
			return outcomes;
		}
		for (String line : Files.readAllLines(sidecar, StandardCharsets.UTF_8)) {
			parse(line, outcomes);
		}
		return outcomes;
	}

	private static void parse(String line, Map<String, Integer> outcomes) {
		String[] fields = line.split(SEPARATOR, 2);
		if (fields.length == 2 && !fields[0].isBlank()) {
			count(fields[1]).ifPresent(violations -> outcomes.put(fields[0], violations));
		}
	}

	private static Optional<Integer> count(String field) {
		try {
			return Optional.of(Integer.valueOf(field.strip()));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static void write(Path sidecar, Map<String, Integer> outcomes) throws IOException {
		List<String> lines = outcomes.entrySet().stream()
				.map(outcome -> outcome.getKey() + SEPARATOR + outcome.getValue())
				.toList();
		Files.write(sidecar, lines, StandardCharsets.UTF_8);
	}

	static String render(Map<String, Integer> outcomes) {
		Map<String, Integer> ordered = new TreeMap<>(outcomes);
		HtmlPage page = new HtmlPage(TITLE, CSS);
		appendSummary(page.body(), ordered);
		appendTable(page.body(), ordered);
		return page.render();
	}

	private static void appendSummary(StringBuilder html, Map<String, Integer> outcomes) {
		long failed = outcomes.values().stream().filter(violations -> violations > 0).count();
		String status = failed == 0 ? "passed" : "failed";
		html.append("<p class=\"status ").append(status).append("\">")
				.append(outcomes.size() - failed).append(" of ").append(outcomes.size())
				.append(" rule(s) passed</p>\n");
	}

	private static void appendTable(StringBuilder html, Map<String, Integer> outcomes) {
		html.append("<table>\n<thead>\n<tr><th>Rule</th><th>Violations</th></tr>\n</thead>\n<tbody>\n");
		outcomes.forEach((rule, violations) -> appendRow(html, rule, violations));
		html.append("</tbody>\n</table>\n");
	}

	private static void appendRow(StringBuilder html, String rule, int violations) {
		String cell = violations == 0 ? "<td class=\"ok\">none</td>"
				: "<td class=\"bad\">" + violations + "</td>";
		html.append("<tr><td><a href=\"").append(escape(rule)).append(".html\">").append(escape(rule))
				.append("</a></td>").append(cell).append("</tr>\n");
	}
}

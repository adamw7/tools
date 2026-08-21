package io.github.adamw7.tools.enforcer.rule;

/**
 * The document both report pages are written into: the head, the styling and the
 * escaping they share. A page contributes its own title and body; the frame around
 * it is written once, so a per-rule report and the index that links to it cannot
 * come to look like pages from different tools.
 */
final class HtmlPage {

	/**
	 * The rules the pages share. Each adds what only it uses — the index its
	 * pass/fail cells, a report its numbered columns — through {@link #style}.
	 */
	private static final String COMMON_CSS =
			"body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;"
			+ "line-height:1.5;color:#1a1a1a;background:#f6f7f9;margin:0;padding:2rem;}"
			+ "main{max-width:52rem;margin:0 auto;background:#fff;padding:1.5rem 2rem;"
			+ "border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.1);}"
			+ "h1{font-size:1.5rem;margin:0 0 .5rem;}"
			+ ".status{display:inline-block;font-weight:600;padding:.25rem .75rem;border-radius:999px;}"
			+ ".status.failed{background:#fdecea;color:#b3261e;}"
			+ ".status.passed{background:#e6f4ea;color:#1e7e34;}"
			+ "table{border-collapse:collapse;width:100%;}"
			+ "th,td{border:1px solid #e0e0e0;padding:.5rem .75rem;text-align:left;}"
			+ "thead th{background:#f0f1f3;font-weight:600;}";

	private final StringBuilder html = new StringBuilder();

	HtmlPage(String title, String style) {
		html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
		html.append("<meta charset=\"utf-8\">\n");
		html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
		html.append("<title>").append(escape(title)).append("</title>\n");
		html.append("<style>").append(COMMON_CSS).append(style).append("</style>\n");
		html.append("</head>\n<body>\n<main>\n");
		html.append("<h1>").append(escape(title)).append("</h1>\n");
	}

	/** The body so far, for a page still adding to it. */
	StringBuilder body() {
		return html;
	}

	/** The finished document, closed off after the body. */
	String render() {
		return html + "</main>\n</body>\n</html>\n";
	}

	/** Escapes the five characters that are significant in HTML text and attributes. */
	static String escape(String text) {
		return text.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}
}

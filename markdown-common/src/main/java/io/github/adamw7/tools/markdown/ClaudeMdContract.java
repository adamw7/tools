package io.github.adamw7.tools.markdown;

import java.util.List;

/**
 * The values of the {@code CLAUDE.md} contract: the enforcer's {@code claudeMdFormat}
 * rule checks a document against them and the adoption's conformer writes one to
 * them. They are stated once here, in the module both depend on, so the checker and
 * the rewriter cannot come to disagree about them. The adoption does not depend on
 * the enforcer, because a pipeline that shipped an enforcer rule would force the
 * maven-enforcer API on every consumer.
 */
public final class ClaudeMdContract {

	/** The document's file name. */
	public static final String FILE_NAME = "CLAUDE.md";

	/** The title the document opens with. */
	public static final String TITLE = "# " + FILE_NAME;

	/** The companion document it points at unless a project configures otherwise. */
	public static final String COMPANION = "AGENTS.md";

	/**
	 * The sections required by default. They are Java and Maven sections because the
	 * rule's defaults are a Java project's; a project arranged differently configures
	 * its own.
	 */
	public static final List<String> REQUIRED_SECTIONS = List.of(
			"## Project",
			"## Java version",
			"## Maven",
			"## Principles for Java Development",
			"## Testing",
			"## Dependencies");

	private ClaudeMdContract() {
	}
}

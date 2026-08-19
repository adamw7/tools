package io.github.adamw7.tools.adopt.step;

import java.util.List;

import io.github.adamw7.tools.markdown.MarkdownContract;
import io.github.adamw7.tools.markdown.MarkdownConformer;

/**
 * The {@code CLAUDE.md} contract as the adoption states it, reshaped by the
 * shared {@link MarkdownConformer}.
 *
 * <p>A generic {@code claude init} writes natural, project-specific headings and
 * no {@code AGENTS.md} reference, while the {@code claudeMdFormat} rule
 * {@link EnforcerStep} wires in demands a fixed set of whole-line headings plus
 * that reference — so without this reshape the adoption fails its own
 * {@link VerifyStep}.
 *
 * <p>Only the contract lives here; the reshape is {@code markdown-common}'s,
 * alongside the {@link io.github.adamw7.tools.markdown.MarkdownDocument} reader the
 * rule judges the result with. A second implementation in this module was kept
 * honest by a contract test, which caught the drift only after it had been written
 * twice.
 *
 * <p>Which sections it demands is the caller's to choose, because the guard being
 * wired in differs by build system — see
 * {@link BuildSystem#requiredClaudeMdSections()}. The title and the
 * {@code AGENTS.md} reference are settled either way, since the step writes a
 * companion {@code AGENTS.md} for every adopted repository.
 */
public class ClaudeMdConformer {

	/*
	 * These mirror io.github.adamw7.tools.enforcer.doc.ClaudeMdFormatRule in the
	 * claude-code-enforcer module. They are restated rather than imported because the
	 * adopt module does not depend on the enforcer module — a pipeline that shipped an
	 * enforcer rule would force the maven-enforcer API on every consumer. What is
	 * restated is now only the contract's values; the reshape that satisfies them is
	 * markdown-common's, which both modules do depend on. The copy is not trusted to
	 * stay in step: ClaudeMdConformerContractTest runs the real rule over this class's
	 * output, so a rule that asks for one more section fails this module's build
	 * rather than some adopted repository's.
	 */
	static final String TITLE = "# CLAUDE.md";
	static final String AGENTS_REFERENCE = "AGENTS.md";

	/**
	 * The sections the {@code claudeMdFormat} rule demands, and so the sections to
	 * conform to <em>where that rule is the guard being wired in</em> — the Maven
	 * path, {@link MavenBuildSystem#requiredClaudeMdSections()}. They are Java and
	 * Maven sections because the rule is a Java project's rule; a build system whose
	 * guard asks only that the file exist and carry something requires none.
	 */
	static final List<String> REQUIRED_SECTIONS = List.of(
			"## Project",
			"## Java version",
			"## Maven",
			"## Principles for Java Development",
			"## Testing",
			"## Dependencies");

	static final String AGENTS_REFERENCE_LINE = "See [AGENTS.md](AGENTS.md) for the companion agent guide.";

	/** @see MarkdownContract#DEFAULT_STUB_BODY */
	static final String STUB_BODY = MarkdownContract.DEFAULT_STUB_BODY;

	private final MarkdownConformer conformer;

	/** Conforms to the full {@code claudeMdFormat} contract. */
	public ClaudeMdConformer() {
		this(REQUIRED_SECTIONS);
	}

	/**
	 * Conforms only as far as the guard being wired in actually demands. Stamping the
	 * {@code claudeMdFormat} sections onto every adopted repository put Java and Maven
	 * headings, each carrying nothing but {@link #STUB_BODY}, into projects that are
	 * neither — where nothing then checked them, the Gradle and GitHub Actions guards
	 * asking only that the file exist and carry something.
	 *
	 * @param requiredSections the headings to canonicalize, append and give a body to,
	 *                         empty for a guard that demands no particular section. A
	 *                         heading named twice is kept once: each is settled against
	 *                         the document as it stood before the pass, so a repeated
	 *                         one would have had a second near-miss renamed to it.
	 */
	public ClaudeMdConformer(List<String> requiredSections) {
		this.conformer = new MarkdownConformer(contractFor(requiredSections));
	}

	/**
	 * @return {@code content} reshaped so the {@code claudeMdFormat} rule passes,
	 *         with a single trailing newline
	 */
	public String conform(String content) {
		return conformer.conform(content);
	}

	/**
	 * The reference line is stated rather than derived, because it names the companion
	 * guide the adoption installs and says what that guide is for. The rule only asks
	 * that {@code AGENTS.md} be mentioned in prose.
	 */
	private static MarkdownContract contractFor(List<String> requiredSections) {
		return MarkdownContract.titled(TITLE)
				.requiring(requiredSections)
				.referencing(AGENTS_REFERENCE, AGENTS_REFERENCE_LINE);
	}
}

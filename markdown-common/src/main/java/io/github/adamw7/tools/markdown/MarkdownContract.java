package io.github.adamw7.tools.markdown;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * The shape a Markdown document is required to have: the title it opens with, the
 * section headings it must carry with a body under each, and optionally a
 * companion document it must reference.
 *
 * <p>It is stated once and read by both sides of the same contract — the check
 * that fails a document not shaped this way, and the {@link MarkdownConformer}
 * that reshapes one into it. Two statements of it drifted, and every drift
 * produced the same bug: a document reshaped to satisfy a check that had since
 * come to ask for something else.
 *
 * @param title             the heading the document must open with, e.g. {@code # CLAUDE.md}
 * @param requiredSections  the headings it must carry, in the order they are appended;
 *                          a heading named twice counts once
 * @param requiredReference a companion document the prose must mention, or empty for none
 * @param referenceLine     the line inserted when {@code requiredReference} is missing
 * @param stubBody          what a required section with nothing under it is given, since a
 *                          check that fails an empty section fails it as firmly as a missing one
 */
public record MarkdownContract(String title, List<String> requiredSections, String requiredReference,
		String referenceLine, String stubBody) {

	/**
	 * What a required section the project has nothing to say under is given. Saying
	 * plainly that nothing is recorded yet is both true and actionable — a stub that
	 * pointed at a companion document, which pointed back, left a reader unable to
	 * tell an unanswered section from an answered one.
	 */
	public static final String DEFAULT_STUB_BODY =
			"_Not documented yet — replace this line with the project's own guidance._";

	public MarkdownContract {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(requiredReference, "requiredReference");
		Objects.requireNonNull(referenceLine, "referenceLine");
		Objects.requireNonNull(stubBody, "stubBody");
		requiredSections = List.copyOf(new LinkedHashSet<>(requiredSections));
	}

	/** A contract asking only that the document open with {@code title}. */
	public static MarkdownContract titled(String title) {
		return new MarkdownContract(title, List.of(), "", "", DEFAULT_STUB_BODY);
	}

	/**
	 * @param sections the headings to require, empty for a check that demands none.
	 *                 Stamping headings onto a document whose check does not ask for
	 *                 them leaves sections carrying nothing but the stub, which
	 *                 nothing then reads.
	 */
	public MarkdownContract requiring(List<String> sections) {
		return new MarkdownContract(title, sections, requiredReference, referenceLine, stubBody);
	}

	/**
	 * Requires the prose to mention {@code document}, inserting the conventional link
	 * to it when it does not. A blank name requires no reference at all, for a project
	 * that keeps no companion document.
	 */
	public MarkdownContract referencing(String document) {
		String reference = document == null ? "" : document.strip();
		return new MarkdownContract(title, requiredSections, reference, referenceLineFor(reference), stubBody);
	}

	/** Requires the reference and states verbatim the line to insert when it is absent. */
	public MarkdownContract referencing(String document, String referenceLine) {
		return new MarkdownContract(title, requiredSections, document.strip(), referenceLine, stubBody);
	}

	public MarkdownContract withStubBody(String stubBody) {
		return new MarkdownContract(title, requiredSections, requiredReference, referenceLine, stubBody);
	}

	/** @return whether the document must mention a companion document at all */
	public boolean requiresReference() {
		return !requiredReference.isEmpty();
	}

	private static String referenceLineFor(String document) {
		if (document.isEmpty()) {
			return "";
		}
		return "See [" + document + "](" + document + ") for the companion agent guide.";
	}
}

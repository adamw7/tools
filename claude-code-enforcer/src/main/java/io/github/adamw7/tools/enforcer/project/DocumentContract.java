package io.github.adamw7.tools.enforcer.project;

import java.util.List;

/**
 * What {@link ClaudeCodeProjectRule} was told about the project's documents, as
 * opposed to what {@link ProjectLayout} works out for itself. Everything else the
 * composite runs is decided by convention; these four cannot be, and they travel
 * together so the parts stay a list of parts rather than of parameters.
 *
 * @param autoFix        whether the document parts repair what they can
 * @param budgetBytes    the size {@code CLAUDE.md} is held to; zero to not check
 * @param claudeMdSections the headings it must carry, or null for the rule's defaults
 * @param claudeMdReference the companion document it must mention, or null for the default
 */
record DocumentContract(boolean autoFix, int budgetBytes, List<String> claudeMdSections,
		String claudeMdReference) {
}

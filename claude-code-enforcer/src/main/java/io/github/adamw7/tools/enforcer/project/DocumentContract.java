package io.github.adamw7.tools.enforcer.project;

import java.util.List;

/**
 * What {@link ClaudeCodeProjectRule} was told about the project's documents, as
 * opposed to what {@link ProjectLayout} could work out for itself.
 *
 * <p>Everything else the composite runs is decided by convention — where a file
 * lives, whether it is there. These four cannot be: what a project's {@code
 * CLAUDE.md} is about, which companion document it points at, how big it may be,
 * and whether the run may repair it. They travel together because they are settled
 * in one place and read in another, and passing four arguments through would make
 * the parts a list of parameters rather than a list of parts.
 *
 * @param autoFix        whether the document parts repair what they can
 * @param budgetBytes    the size {@code CLAUDE.md} is held to; zero to not check
 * @param claudeMdSections the headings it must carry, or null for the rule's defaults
 * @param claudeMdReference the companion document it must mention, or null for the default
 */
record DocumentContract(boolean autoFix, int budgetBytes, List<String> claudeMdSections,
		String claudeMdReference) {
}

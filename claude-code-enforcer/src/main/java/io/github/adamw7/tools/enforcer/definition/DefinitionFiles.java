package io.github.adamw7.tools.enforcer.definition;

import java.io.File;

/**
 * What a skill looks like on disk. A skill is the one Claude Code definition that
 * is a directory rather than a file, so the {@code SKILL.md} inside it has to be
 * named by both rules that walk skills — {@link SkillFilesExistRule}, which
 * validates one, and {@link MultiDefinitionRule}, which visits every definition of
 * every kind — and naming it here keeps the two from drifting apart.
 * <p>
 * The listing, {@code .md} and directory-existence helpers these rules also use
 * are shared with the rest of the module and live in
 * {@link io.github.adamw7.tools.enforcer.rule.ProjectFiles}.
 */
final class DefinitionFiles {

	static final String SKILL_FILE_NAME = "SKILL.md";

	private DefinitionFiles() {
	}

	/** The definition a skill directory carries. */
	static File skillFile(File skillDirectory) {
		return new File(skillDirectory, SKILL_FILE_NAME);
	}
}

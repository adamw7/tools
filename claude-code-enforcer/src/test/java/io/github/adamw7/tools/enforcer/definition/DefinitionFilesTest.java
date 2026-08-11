package io.github.adamw7.tools.enforcer.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefinitionFilesTest {

	@TempDir
	private Path tempDir;

	@Test
	void skillFileIsTheSkillMarkdownInsideTheSkillDirectory() {
		File skillDirectory = tempDir.resolve("git-commit").toFile();

		assertEquals(new File(skillDirectory, "SKILL.md"), DefinitionFiles.skillFile(skillDirectory));
	}

	@Test
	void skillFileNamesTheDefinitionEveryRuleWalkingSkillsLooksFor() {
		assertEquals("SKILL.md", DefinitionFiles.SKILL_FILE_NAME);
	}
}

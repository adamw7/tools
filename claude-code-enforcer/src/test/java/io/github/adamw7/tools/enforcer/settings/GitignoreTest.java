package io.github.adamw7.tools.enforcer.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GitignoreTest {

	@Test
	void matchesAVerbatimPath() {
		assertTrue(Gitignore.parse(".claude/settings.local.json").covers(".claude/settings.local.json"));
	}

	@Test
	void aPatternWithoutASlashMatchesAtAnyDepth() {
		assertTrue(Gitignore.parse("settings.local.json").covers(".claude/settings.local.json"));
	}

	@Test
	void aPatternWithASlashIsAnchoredToTheRoot() {
		Gitignore gitignore = Gitignore.parse("claude/settings.local.json");
		assertFalse(gitignore.covers("nested/claude/settings.local.json"));
		assertTrue(gitignore.covers("claude/settings.local.json"));
	}

	@Test
	void aLeadingSlashAnchorsToTheRoot() {
		Gitignore gitignore = Gitignore.parse("/derby.log");
		assertTrue(gitignore.covers("derby.log"));
		assertFalse(gitignore.covers("data/derby.log"));
	}

	@Test
	void starDoesNotCrossSegments() {
		Gitignore gitignore = Gitignore.parse(".claude/*.json");
		assertTrue(gitignore.covers(".claude/settings.json"));
		assertFalse(gitignore.covers(".claude/nested/deep.json"));
	}

	@Test
	void starMatchesADirectoryAndSoCoversEverythingBeneathIt() {
		assertTrue(Gitignore.parse(".claude/*").covers(".claude/nested/deep.json"));
	}

	@Test
	void doubleStarCrossesSegments() {
		assertTrue(Gitignore.parse("**/settings.local.json").covers(".claude/settings.local.json"));
		assertTrue(Gitignore.parse(".claude/**").covers(".claude/nested/deep.json"));
	}

	@Test
	void questionMarkMatchesASingleCharacter() {
		Gitignore gitignore = Gitignore.parse("file?.txt");
		assertTrue(gitignore.covers("file1.txt"));
		assertFalse(gitignore.covers("file12.txt"));
	}

	@Test
	void aDirectoryPatternCoversFilesBeneathIt() {
		Gitignore gitignore = Gitignore.parse("target/");
		assertTrue(gitignore.covers("target/classes/App.class"));
		assertFalse(gitignore.covers("target"));
	}

	@Test
	void commentsAndBlankLinesAreSkipped() {
		assertFalse(Gitignore.parse("# a comment\n\n").covers("a comment"));
	}

	@Test
	void aLaterNegationWins() {
		Gitignore gitignore = Gitignore.parse(".mvn/*\n!.mvn/maven.config");
		assertTrue(gitignore.covers(".mvn/extensions.xml"));
		assertFalse(gitignore.covers(".mvn/maven.config"));
	}

	@Test
	void thisRepositoriesGitignoreShapeCoversTheLocalSettings() {
		Gitignore gitignore = Gitignore.parse("target/\n*.log\n\n.idea/\n\n.claude/settings.local.json");
		assertTrue(gitignore.covers(".claude/settings.local.json"));
		assertFalse(gitignore.covers(".claude/settings.json"));
	}
}

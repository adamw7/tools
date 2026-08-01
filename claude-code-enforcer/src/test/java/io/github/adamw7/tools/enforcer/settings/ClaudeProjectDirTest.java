package io.github.adamw7.tools.enforcer.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What both hook rules read a hook command as: which of its tokens name a script
 * inside the repository, and where each resolves to. The rules themselves only
 * decide what to say about the answer, so the resolution is pinned here rather
 * than twice over through them.
 */
class ClaudeProjectDirTest {

	private static final String HOOK = ".claude/hooks/session-start.sh";

	@TempDir
	private Path projectDir;

	@Test
	void resolvesAScriptRootedAtTheProjectVariable() {
		assertEquals(List.of(resolved(HOOK)), scriptsIn("$CLAUDE_PROJECT_DIR/" + HOOK));
	}

	@Test
	void resolvesAScriptRootedAtTheBracedProjectVariable() {
		assertEquals(List.of(resolved(HOOK)), scriptsIn("${CLAUDE_PROJECT_DIR}/" + HOOK));
	}

	/**
	 * Claude Code runs a hook from the project directory, so a bare relative path
	 * names the same file the variable spells out. A settings.json is as likely to be
	 * written either way, and reading only the variable left the other unchecked.
	 */
	@Test
	void resolvesAPlainRepositoryRelativeProgram() {
		assertEquals(List.of(resolved(HOOK)), scriptsIn(HOOK));
	}

	@Test
	void resolvesADotSlashProgram() {
		assertEquals(List.of(resolved("./run.sh")), scriptsIn("./run.sh"));
	}

	@Test
	void resolvesAQuotedRelativeProgram() {
		assertEquals(List.of(resolved(HOOK)), scriptsIn("\"" + HOOK + "\""));
	}

	@Test
	void resolvesTheProgramOfEveryChainedCommand() {
		assertEquals(List.of(resolved("a.d/a.sh"), resolved("b.d/b.sh")), scriptsIn("a.d/a.sh && b.d/b.sh"));
	}

	/**
	 * The two spellings name one script, so a command using both must not have it
	 * reported — or baselined — twice.
	 */
	@Test
	void namesAScriptOnceWhenACommandUsesBothSpellings() {
		assertEquals(List.of(resolved(HOOK)), scriptsIn("$CLAUDE_PROJECT_DIR/" + HOOK + " && " + HOOK));
	}

	@Test
	void readsNoScriptFromABareProgramName() {
		assertEquals(List.of(), scriptsIn("npx some-linter"));
	}

	/** Only a program is a script; an argument the hook is about to write is not. */
	@Test
	void readsNoScriptFromAnArgumentThatLooksLikeAPath() {
		assertEquals(List.of(resolved("build.d/build.sh")), scriptsIn("build.d/build.sh --out target/log.txt"));
	}

	@Test
	void readsNoScriptFromAnInterpretersArgument() {
		assertEquals(List.of(), scriptsIn("bash " + HOOK));
	}

	@Test
	void readsNoScriptFromAnAbsoluteProgram() {
		assertEquals(List.of(), scriptsIn("/usr/local/bin/lint"));
	}

	@Test
	void readsNoScriptFromAHomeRelativeProgram() {
		assertEquals(List.of(), scriptsIn("~/bin/lint.sh"));
	}

	/** Only a shell can resolve another variable, so the path it would produce is unknown. */
	@Test
	void readsNoScriptFromAProgramCarryingAnotherExpansion() {
		assertEquals(List.of(), scriptsIn("$HOME/bin/lint.sh"));
	}

	@Test
	void readsNoScriptFromAVariableThatMerelyStartsTheSameWay() {
		assertEquals(List.of(), scriptsIn("bash $CLAUDE_PROJECT_DIR_BACKUP/run.sh"));
	}

	@Test
	void readsNoScriptFromABlankCommand() {
		assertEquals(List.of(), scriptsIn("   "));
	}

	@Test
	void prefersTheConfiguredProjectDirectoryOverTheSettingsLocation() {
		File override = projectDir.resolve("elsewhere").toFile();
		ClaudeProjectDir resolver = new ClaudeProjectDir(override, settingsFile());

		assertEquals(override, resolver.resolve());
	}

	/** Settings live at {@code <project>/.claude/settings.json}, so the project is two levels up. */
	@Test
	void fallsBackToTheGrandparentOfTheSettingsFile() {
		ClaudeProjectDir resolver = new ClaudeProjectDir(null, settingsFile());

		assertEquals(projectDir.toFile().getAbsoluteFile(), resolver.resolve());
	}

	@Test
	void resolvesAgainstTheGrandparentWhenNoProjectDirectoryIsConfigured() {
		List<String> scripts = new ClaudeProjectDir(null, settingsFile()).scriptsIn(HOOK);

		assertEquals(1, scripts.size(), scripts.toString());
		assertTrue(scripts.getFirst().endsWith(new File(HOOK).getPath()), scripts.toString());
	}

	private List<String> scriptsIn(String command) {
		return new ClaudeProjectDir(projectDir.toFile(), settingsFile()).scriptsIn(command);
	}

	private String resolved(String relativePath) {
		return new File(projectDir.toFile(), relativePath).getPath();
	}

	private File settingsFile() {
		return projectDir.resolve(".claude").resolve("settings.json").toFile();
	}
}

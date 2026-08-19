package io.github.adamw7.tools.enforcer.project;

import static io.github.adamw7.tools.test.TestFiles.writeBytes;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where the composite looks, and the one question it asks of a file's content. */
class ProjectLayoutTest {

	@TempDir
	private Path tempDir;

	@Test
	void resolvesEveryPathAgainstTheProjectDirectory() {
		ProjectLayout layout = new ProjectLayout(tempDir.toFile());

		assertEquals(tempDir.resolve("CLAUDE.md").toFile(), layout.claudeMd());
		assertEquals(tempDir.resolve("AGENTS.md").toFile(), layout.agentsMd());
		assertEquals(tempDir.resolve(".mcp.json").toFile(), layout.mcpJson());
		assertEquals(tempDir.resolve(".claude/settings.json").toFile(), layout.settingsJson());
		assertEquals(tempDir.resolve(".claude/skills").toFile(), layout.skillsDir());
		assertEquals(tempDir.resolve(".claude/agents").toFile(), layout.agentsDir());
		assertEquals(tempDir.resolve(".claude/commands").toFile(), layout.commandsDir());
		assertEquals(tempDir.resolve(".claude/hooks").toFile(), layout.hooksDir());
		assertEquals(tempDir.resolve(".claude-plugin/plugin.json").toFile(), layout.pluginJson());
	}

	@Test
	void readsAnAggregatorPomAsDeclaringModules() {
		writeString(tempDir.resolve("pom.xml"), "<project><modules><module>data</module></modules></project>");

		assertTrue(new ProjectLayout(tempDir.toFile()).declaresModules());
	}

	/** A single-module project has a pom and no module map, which is not a fault. */
	@Test
	void readsASingleModulePomAsDeclaringNone() {
		writeString(tempDir.resolve("pom.xml"), "<project><artifactId>one</artifactId></project>");

		assertFalse(new ProjectLayout(tempDir.toFile()).declaresModules());
	}

	@Test
	void declaresNoModulesWhenThereIsNoPomAtAll() {
		assertFalse(new ProjectLayout(tempDir.toFile()).declaresModules());
	}

	/** A pom that cannot be read is left to the rule that reads it properly to explain. */
	@Test
	void declaresNoModulesWhenThePomCannotBeRead() {
		writeBytes(tempDir.resolve("pom.xml"), new byte[] { (byte) 0xC3, (byte) 0x28 });

		assertFalse(new ProjectLayout(tempDir.toFile()).declaresModules());
	}
}

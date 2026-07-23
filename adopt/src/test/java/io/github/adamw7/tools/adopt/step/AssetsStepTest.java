package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class AssetsStepTest {

	private final AssetsStep step = new AssetsStep();

	@Test
	void installsEveryDefaultAsset(@TempDir Path workspace) throws IOException {
		AdoptionContext context = contextFor(workspace);
		step.execute(context, new RecordingCommandRunner());
		Path checkout = context.repositoryDirectory();
		assertTrue(Files.isRegularFile(checkout.resolve(AdoptionAssets.AGENTS_MD_FILE)));
		assertTrue(Files.isRegularFile(checkout.resolve(AdoptionAssets.SETTINGS_FILE)));
		assertTrue(Files.isRegularFile(checkout.resolve(AdoptionAssets.SESSION_START_HOOK_FILE)));
		assertTrue(Files.isRegularFile(checkout.resolve(AdoptionAssets.MCP_CONFIG_FILE)));
		assertTrue(Files.isRegularFile(checkout.resolve(AdoptionAssets.CLAUDE_WORKFLOW_FILE)));
	}

	@Test
	void runsNoExternalCommands(@TempDir Path workspace) throws IOException {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		step.execute(contextFor(workspace), runner);
		assertEquals(0, runner.count());
	}

	@Test
	void installedJsonAssetsAreValidJson(@TempDir Path workspace) throws IOException {
		AdoptionContext context = contextFor(workspace);
		step.execute(context, new RecordingCommandRunner());
		ObjectMapper mapper = new ObjectMapper();
		Path checkout = context.repositoryDirectory();
		assertTrue(mapper.readTree(Files.readString(checkout.resolve(AdoptionAssets.SETTINGS_FILE))).isObject());
		assertTrue(mapper.readTree(Files.readString(checkout.resolve(AdoptionAssets.MCP_CONFIG_FILE)))
				.get("mcpServers").isObject());
	}

	@Test
	void sessionStartHookIsExecutable(@TempDir Path workspace) throws IOException {
		AdoptionContext context = contextFor(workspace);
		step.execute(context, new RecordingCommandRunner());
		assertTrue(Files.isExecutable(context.repositoryDirectory()
				.resolve(AdoptionAssets.SESSION_START_HOOK_FILE)));
	}

	@Test
	void settingsWireTheSessionStartHook(@TempDir Path workspace) throws IOException {
		AdoptionContext context = contextFor(workspace);
		step.execute(context, new RecordingCommandRunner());
		String settings = Files.readString(context.repositoryDirectory().resolve(AdoptionAssets.SETTINGS_FILE));
		assertTrue(settings.contains(AdoptionAssets.SESSION_START_HOOK_FILE));
	}

	@Test
	void isIdempotentAcrossReRuns(@TempDir Path workspace) throws IOException {
		AdoptionContext context = contextFor(workspace);
		step.execute(context, new RecordingCommandRunner());
		Path agentsMd = context.repositoryDirectory().resolve(AdoptionAssets.AGENTS_MD_FILE);
		Files.writeString(agentsMd, "customised\n");
		step.execute(context, new RecordingCommandRunner());
		assertEquals("customised\n", Files.readString(agentsMd));
	}

	@Test
	void installsOnlyConfiguredAssets(@TempDir Path workspace) throws IOException {
		AdoptionContext context = contextFor(workspace);
		new AssetsStep(List.of(new AssetInstaller("only.txt", "content\n")))
				.execute(context, new RecordingCommandRunner());
		assertTrue(Files.isRegularFile(context.repositoryDirectory().resolve("only.txt")));
		assertTrue(Files.notExists(context.repositoryDirectory().resolve(AdoptionAssets.AGENTS_MD_FILE)));
	}

	@Test
	void isNamedAssets() {
		assertEquals("assets", step.name());
	}

	private AdoptionContext contextFor(Path workspace) throws IOException {
		AdoptionContext context = new AdoptionContext("https://github.com/owner/repo.git", workspace);
		Files.createDirectories(context.repositoryDirectory());
		return context;
	}
}

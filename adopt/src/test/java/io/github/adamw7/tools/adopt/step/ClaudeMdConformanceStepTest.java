package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class ClaudeMdConformanceStepTest {

	private AdoptionContext context(Path workspace) throws IOException {
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/security.git", workspace);
		Files.createDirectories(context.repositoryDirectory());
		return context;
	}

	private void writeClaudeMd(AdoptionContext context, String content) throws IOException {
		Files.writeString(context.repositoryDirectory().resolve("CLAUDE.md"), content);
	}

	private String claudeMd(AdoptionContext context) throws IOException {
		return Files.readString(context.repositoryDirectory().resolve("CLAUDE.md"));
	}

	private Path agentsMd(AdoptionContext context) {
		return context.repositoryDirectory().resolve(AdoptionAssets.AGENTS_MD_FILE);
	}

	@Test
	void isNamedConform() {
		assertEquals("conform", new ClaudeMdConformanceStep().name());
	}

	@Test
	void writesAgentsMdAndNormalisesClaudeMd(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		writeClaudeMd(context, "# CLAUDE.md\n\n## Project purpose\n\nA repo.\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		assertTrue(Files.isRegularFile(agentsMd(context)), "a companion AGENTS.md must be written");
		String claudeMd = claudeMd(context);
		assertTrue(claudeMd.contains(ClaudeMdConformer.AGENTS_REFERENCE), "CLAUDE.md must reference AGENTS.md");
		assertTrue(claudeMd.contains("## Project"), "CLAUDE.md must carry the required heading");
	}

	@Test
	void doesNotOverwriteAnExistingAgentsMd(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		writeClaudeMd(context, "# CLAUDE.md\n");
		Files.writeString(agentsMd(context), "# Project's own AGENTS.md\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		assertEquals("# Project's own AGENTS.md\n", Files.readString(agentsMd(context)),
				"the project's own AGENTS.md must win");
	}

	@Test
	void missingClaudeMdAbortsAdoption(@TempDir Path workspace) throws IOException {
		AdoptionContext context = context(workspace);
		ClaudeMdConformanceStep step = new ClaudeMdConformanceStep();
		RecordingCommandRunner runner = new RecordingCommandRunner();
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}
}

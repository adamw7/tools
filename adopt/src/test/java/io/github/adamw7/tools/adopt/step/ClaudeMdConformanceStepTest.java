package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionContexts;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class ClaudeMdConformanceStepTest {

	private void writeClaudeMd(AdoptionContext context, String content) throws IOException {
		AdoptionContexts.write(context, "CLAUDE.md", content);
	}

	private String claudeMd(AdoptionContext context) throws IOException {
		return Files.readString(context.repositoryDirectory().resolve("CLAUDE.md"));
	}

	private Path agentsMd(AdoptionContext context) {
		return context.repositoryDirectory().resolve(AdoptionAssets.AGENTS_MD_FILE);
	}

	/**
	 * A checkout the Maven guard is wired into, and so the one checkout held to the
	 * {@code claudeMdFormat} rule's sections. A checkout with no build file gets the
	 * workflow guard instead, which asks only that {@code CLAUDE.md} be there and
	 * carry something.
	 */
	private AdoptionContext mavenCheckout(Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "pom.xml", "<project/>");
		return context;
	}

	@Test
	void isNamedConform() {
		assertEquals("conform", new ClaudeMdConformanceStep().name());
	}

	@Test
	void writesAgentsMdAndNormalisesClaudeMd(@TempDir Path workspace) throws IOException {
		AdoptionContext context = mavenCheckout(workspace);
		writeClaudeMd(context, "# CLAUDE.md\n\n## Project purpose\n\nA repo.\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		assertTrue(Files.isRegularFile(agentsMd(context)), "a companion AGENTS.md must be written");
		String claudeMd = claudeMd(context);
		assertTrue(claudeMd.contains(ClaudeMdConformer.AGENTS_REFERENCE), "CLAUDE.md must reference AGENTS.md");
		assertTrue(claudeMd.contains("## Project\n"), "CLAUDE.md must carry the required heading");
	}

	/**
	 * The sections come from the guard being wired in, so a Maven checkout — the one
	 * the {@code claudeMdFormat} rule is wired into — is held to all of them.
	 */
	@Test
	void conformsAMavenCheckoutToEveryRequiredSection(@TempDir Path workspace) throws IOException {
		AdoptionContext context = mavenCheckout(workspace);
		writeClaudeMd(context, "# CLAUDE.md\n\nA repo.\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		String claudeMd = claudeMd(context);
		ClaudeMdConformer.REQUIRED_SECTIONS.forEach(section -> assertTrue(claudeMd.contains(section + "\n"),
				() -> section + " must be present: " + claudeMd));
	}

	/**
	 * A repository with no Maven build gets the workflow guard, which asks only that
	 * {@code CLAUDE.md} exist and carry something. Conforming it to the rule's
	 * sections anyway stamped {@code ## Java version}, {@code ## Maven} and
	 * {@code ## Principles for Java Development} onto projects that are neither Java
	 * nor Maven — a static site among them — where nothing then checked them.
	 */
	@Test
	void leavesANonMavenCheckoutFreeOfTheRulesJavaSections(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		writeClaudeMd(context, "# CLAUDE.md\n\n## Structure\n\nOne HTML page.\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		String claudeMd = claudeMd(context);
		ClaudeMdConformer.REQUIRED_SECTIONS.forEach(section -> assertFalse(claudeMd.contains(section + "\n"),
				() -> section + " must not be added to a project with no Maven guard: " + claudeMd));
		assertTrue(claudeMd.contains("## Structure"), "the generated document's own sections must survive");
		assertTrue(claudeMd.contains(ClaudeMdConformer.AGENTS_REFERENCE),
				"the companion AGENTS.md must still be referenced");
	}

	/**
	 * Conforming to no required section leaves the body {@code claude init} wrote,
	 * blank line and all, so closing the inserted reference with a blank of its own
	 * put a double blank into the first commit of every such adoption.
	 */
	@Test
	void insertsTheAgentsReferenceWithoutDoublingABlankLine(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		writeClaudeMd(context, "# CLAUDE.md\n\nOne HTML page.\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		assertFalse(claudeMd(context).contains("\n\n\n"), claudeMd(context));
	}

	@Test
	void doesNotOverwriteAnExistingAgentsMd(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		writeClaudeMd(context, "# CLAUDE.md\n");
		Files.writeString(agentsMd(context), "# Project's own AGENTS.md\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		assertEquals("# Project's own AGENTS.md\n", Files.readString(agentsMd(context)),
				"the project's own AGENTS.md must win");
	}

	/**
	 * Re-adopting a repository must not churn a {@code CLAUDE.md} the previous run
	 * already normalised, so the second commit has nothing to record for it.
	 */
	@Test
	void leavesAnAlreadyConformingClaudeMdByteIdentical(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		writeClaudeMd(context, "# CLAUDE.md\n\nSee AGENTS.md.\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		String conformed = claudeMd(context);
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		assertEquals(conformed, claudeMd(context), "a conforming CLAUDE.md must be left untouched");
	}

	/**
	 * The conformer works in LF throughout, so a CRLF {@code CLAUDE.md} came back
	 * with every one of its lines changed — a whole-file diff in the adoption's
	 * first commit, on a file the run only meant to add a heading to.
	 */
	@Test
	void keepsACrlfClaudeMdOnCrlf(@TempDir Path workspace) throws IOException {
		AdoptionContext context = mavenCheckout(workspace);
		writeClaudeMd(context, "# CLAUDE.md\r\n\r\n## Project purpose\r\n\r\nA repo.\r\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		String claudeMd = claudeMd(context);
		assertTrue(claudeMd.contains("\r\n"), "the file's CRLF terminators must survive the reshape");
		assertFalse(claudeMd.replace("\r\n", "").contains("\n"), "no line may be left on a bare LF");
		assertTrue(claudeMd.contains("## Project\r\n"), "the reshape must still have run");
	}

	/** An already conforming CRLF file must be recognised as conforming, not rewritten. */
	@Test
	void leavesAnAlreadyConformingCrlfClaudeMdByteIdentical(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		writeClaudeMd(context, "# CLAUDE.md\r\n\r\nSee AGENTS.md.\r\n");
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		String conformed = claudeMd(context);
		new ClaudeMdConformanceStep().execute(context, new RecordingCommandRunner());
		assertEquals(conformed, claudeMd(context), "a conforming CLAUDE.md must be left untouched");
	}

	@Test
	void missingClaudeMdAbortsAdoption(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		ClaudeMdConformanceStep step = new ClaudeMdConformanceStep();
		RecordingCommandRunner runner = new RecordingCommandRunner();
		assertThrows(AdoptionException.class, () -> step.execute(context, runner));
	}
}

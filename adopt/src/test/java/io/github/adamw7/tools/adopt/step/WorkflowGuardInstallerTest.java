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

import io.github.adamw7.tools.adopt.AdoptionException;

class WorkflowGuardInstallerTest {

	private final WorkflowGuardInstaller installer = new WorkflowGuardInstaller();

	@Test
	void writesTheWorkflowThatRunsTheGuardScript(@TempDir Path directory) throws IOException {
		assertTrue(installer.install(directory));
		String workflow = Files.readString(directory.resolve(WorkflowGuardInstaller.WORKFLOW_FILE));
		assertTrue(workflow.contains("uses: actions/checkout@v4"));
		assertTrue(workflow.contains("run: sh " + WorkflowGuardInstaller.SCRIPT_FILE));
	}

	@Test
	void writesTheGuardScriptThatFailsOnAMissingOrEmptyClaudeMd(@TempDir Path directory) throws IOException {
		assertTrue(installer.install(directory));
		String script = Files.readString(directory.resolve(WorkflowGuardInstaller.SCRIPT_FILE));
		assertTrue(script.startsWith("#!/bin/sh"));
		assertTrue(script.contains("grep -q '[^[:space:]]' CLAUDE.md"));
		assertTrue(script.contains("CLAUDE.md is missing or empty"));
	}

	/**
	 * Both files are written verbatim, so their terminators are whatever the templates
	 * carry — nothing re-terminates them against a file already in the checkout. LF is
	 * the answer on every platform: {@code sh} reads a CRLF shebang as an interpreter
	 * named {@code /bin/sh\r} and refuses the script, which would fail the guard the
	 * adoption just installed on the run's own machine.
	 */
	@Test
	void writesBothGuardFilesWithLfEndingsAndTheAdoptionMarker(@TempDir Path directory) throws IOException {
		assertTrue(installer.install(directory));
		String workflow = Files.readString(directory.resolve(WorkflowGuardInstaller.WORKFLOW_FILE));
		String script = Files.readString(directory.resolve(WorkflowGuardInstaller.SCRIPT_FILE));
		assertFalse(workflow.contains("\r"), "the workflow must be LF, whatever platform the adoption ran on");
		assertFalse(script.contains("\r"), "the script must be LF; sh refuses a CRLF shebang line");
		assertTrue(workflow.contains("# Added by claude-code-adopt:"), "the workflow must name its author");
		assertTrue(script.contains("# Added by claude-code-adopt:"), "the script must name its author");
	}

	/**
	 * The fallback guard is the only thing standing between a build-less repository
	 * and an unchecked CLAUDE.md, so a checkout it cannot be written into must fail
	 * the adoption rather than pass silently guarded by nothing.
	 */
	@Test
	void aCheckoutTheGuardCannotBeWrittenIntoAbortsAdoption(@TempDir Path directory) throws IOException {
		Files.createDirectories(directory.resolve(".github"));
		Files.writeString(directory.resolve(".github/workflows"), "not a directory");
		assertThrows(AdoptionException.class, () -> installer.install(directory));
	}

	@Test
	void leavesAnAlreadyGuardedCheckoutUnchanged(@TempDir Path directory) throws IOException {
		installer.install(directory);
		String afterFirstInstall = Files.readString(directory.resolve(WorkflowGuardInstaller.WORKFLOW_FILE));
		assertFalse(installer.install(directory));
		assertEquals(afterFirstInstall, Files.readString(directory.resolve(WorkflowGuardInstaller.WORKFLOW_FILE)));
	}

	/**
	 * The project's own file at the guard's path always wins, as it does for every
	 * other file the adoption writes; overwriting it destroyed a workflow the
	 * adoption never wrote.
	 */
	@Test
	void keepsAWorkflowTheProjectAlreadyCarries(@TempDir Path directory) throws IOException {
		Path workflowFile = directory.resolve(WorkflowGuardInstaller.WORKFLOW_FILE);
		Files.createDirectories(workflowFile.getParent());
		String own = "name: my own guard\non: [push]\njobs: {}\n";
		Files.writeString(workflowFile, own);
		assertTrue(installer.install(directory), "the guard script is still installed alongside it");
		assertEquals(own, Files.readString(workflowFile));
	}

	/**
	 * The script is what {@link FallbackBuildSystem#verifyCommand()} runs, so a
	 * checkout that kept the workflow but lost the script has to get the script
	 * back rather than fail the adoption on a file that is not there.
	 */
	@Test
	void writesBackAGuardScriptThatWentMissing(@TempDir Path directory) throws IOException {
		installer.install(directory);
		Path scriptFile = directory.resolve(WorkflowGuardInstaller.SCRIPT_FILE);
		Files.delete(scriptFile);
		assertTrue(installer.install(directory));
		assertTrue(Files.isRegularFile(scriptFile));
	}

	/** The verification reads both files, because the workflow runs the script and either alone guards nothing. */
	@Test
	void readsACheckoutCarryingBothGuardFilesAsGuarded(@TempDir Path directory) {
		installer.install(directory);

		assertTrue(installer.isInstalled(directory));
	}

	@Test
	void readsACheckoutCarryingNeitherGuardFileAsUnguarded(@TempDir Path directory) {
		assertFalse(installer.isInstalled(directory));
	}

	@Test
	void readsACheckoutThatLostTheGuardScriptAsUnguarded(@TempDir Path directory) throws IOException {
		installer.install(directory);
		Files.delete(directory.resolve(WorkflowGuardInstaller.SCRIPT_FILE));

		assertFalse(installer.isInstalled(directory));
	}

	/**
	 * A workflow the adoption did not write is kept but is unlikely to run the script,
	 * so it does not stand in for the guard: the marker is what tells the two apart.
	 */
	@Test
	void readsTheProjectsOwnWorkflowAsNoGuardOfTheAdoptionsOwn(@TempDir Path directory) throws IOException {
		Path workflowFile = directory.resolve(WorkflowGuardInstaller.WORKFLOW_FILE);
		Files.createDirectories(workflowFile.getParent());
		Files.writeString(workflowFile, "name: my own guard\non: [push]\njobs: {}\n");
		installer.install(directory);

		assertFalse(installer.isInstalled(directory));
	}
}

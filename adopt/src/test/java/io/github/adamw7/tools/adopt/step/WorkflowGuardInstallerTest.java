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
}

package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

	@Test
	void leavesAnAlreadyGuardedCheckoutUnchanged(@TempDir Path directory) throws IOException {
		installer.install(directory);
		String afterFirstInstall = Files.readString(directory.resolve(WorkflowGuardInstaller.WORKFLOW_FILE));
		assertFalse(installer.install(directory));
		assertEquals(afterFirstInstall, Files.readString(directory.resolve(WorkflowGuardInstaller.WORKFLOW_FILE)));
	}
}

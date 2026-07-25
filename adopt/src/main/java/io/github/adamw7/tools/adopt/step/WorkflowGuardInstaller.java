package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * Installs a build-tool-agnostic {@code CLAUDE.md} guard into a checkout that has
 * no Maven or Gradle build to wire into. The guard is a GitHub Actions workflow
 * that runs a small portable shell script on every push and pull request, failing
 * the run when the generated {@code CLAUDE.md} is missing or empty. Because the
 * adoption already targets GitHub repositories, a workflow is a guard every
 * adopted repository can run without introducing a build tool.
 *
 * <p>The workflow and the script it runs are both committed, so the guard is
 * shared with every contributor rather than living only in the adopter's local
 * checkout. The script is the single source of truth: the workflow invokes it and
 * {@link FallbackBuildSystem#verifyCommand()} runs the same script locally, so the
 * adoption fails before the branch is pushed just as it would in CI.
 *
 * <p>The two files are installed independently and neither is ever overwritten, so
 * the install is idempotent and a project that already carries a file at one of
 * these paths keeps its own version — the same rule {@link AssetInstaller} applies
 * to every other file the adoption writes. Installing them independently also
 * means a checkout that kept the workflow but lost the script has the script
 * written back, rather than being left for {@link FallbackBuildSystem}'s
 * verification to fail on a file that is not there.
 */
public class WorkflowGuardInstaller {

	private static final Logger log = LogManager.getLogger(WorkflowGuardInstaller.class);

	static final String WORKFLOW_FILE = ".github/workflows/claude-md-guard.yml";
	static final String SCRIPT_FILE = ".github/claude-md-guard.sh";

	private static final String MARKER = "Added by claude-code-adopt";

	private static final String WORKFLOW = """
			# %s: fail CI when CLAUDE.md is missing or empty.
			name: CLAUDE.md guard
			on: [push, pull_request]
			jobs:
			  claude-md-guard:
			    runs-on: ubuntu-latest
			    steps:
			      - uses: actions/checkout@v4
			      - name: Enforce CLAUDE.md
			        run: sh %s
			""".formatted(MARKER, SCRIPT_FILE);

	private static final String SCRIPT = """
			#!/bin/sh
			# %s: fail when CLAUDE.md is missing or empty.
			if [ ! -f CLAUDE.md ] || ! grep -q '[^[:space:]]' CLAUDE.md; then
			  echo "CLAUDE.md is missing or empty" >&2
			  exit 1
			fi
			""".formatted(MARKER);

	private final AssetInstaller workflowInstaller = new AssetInstaller(WORKFLOW_FILE, WORKFLOW);
	private final AssetInstaller scriptInstaller = new AssetInstaller(SCRIPT_FILE, SCRIPT);

	/**
	 * @return {@code true} when either guard file was written, {@code false} when the
	 *         checkout already carried both and was left unchanged.
	 */
	public boolean install(Path repositoryDirectory) {
		boolean workflowWritten = workflowInstaller.install(repositoryDirectory);
		boolean scriptWritten = scriptInstaller.install(repositoryDirectory);
		warnIfWorkflowIsNotOurs(repositoryDirectory, workflowWritten);
		return workflowWritten || scriptWritten;
	}

	/**
	 * A workflow the adoption did not write is kept, but it is unlikely to run the
	 * guard script, so CI may not enforce {@code CLAUDE.md} even though the local
	 * verification passes. Saying so is more useful than silently overwriting the
	 * project's own workflow.
	 */
	private void warnIfWorkflowIsNotOurs(Path repositoryDirectory, boolean workflowWritten) {
		if (!workflowWritten && !carriesMarker(repositoryDirectory.resolve(WORKFLOW_FILE))) {
			log.warn("{} already exists and is not the adoption's workflow; left unchanged, so CI may not run {}",
					WORKFLOW_FILE, SCRIPT_FILE);
		}
	}

	private boolean carriesMarker(Path workflowFile) {
		try {
			return Files.isRegularFile(workflowFile) && Files.readString(workflowFile).contains(MARKER);
		} catch (IOException e) {
			throw new AdoptionException("Could not read workflow file: " + workflowFile, e);
		}
	}
}

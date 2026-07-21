package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * adoption fails before the branch is pushed just as it would in CI. The install
 * is idempotent: a checkout whose workflow already carries the adoption marker is
 * left unchanged.
 */
public class WorkflowGuardInstaller {

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

	/**
	 * @return {@code true} when the guard was written, {@code false} when the
	 *         checkout already carried it and was left unchanged.
	 */
	public boolean install(Path repositoryDirectory) {
		Path workflowFile = repositoryDirectory.resolve(WORKFLOW_FILE);
		if (alreadyGuarded(workflowFile)) {
			return false;
		}
		write(workflowFile, WORKFLOW);
		write(repositoryDirectory.resolve(SCRIPT_FILE), SCRIPT);
		return true;
	}

	private boolean alreadyGuarded(Path workflowFile) {
		return Files.isRegularFile(workflowFile) && read(workflowFile).contains(MARKER);
	}

	private String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new AdoptionException("Could not read workflow file: " + file, e);
		}
	}

	private void write(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new AdoptionException("Could not write guard file: " + file, e);
		}
	}
}

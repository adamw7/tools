package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionFiles;

/**
 * Installs a build-tool-agnostic {@code CLAUDE.md} guard into a checkout that has
 * no Maven or Gradle build to wire into: a GitHub Actions workflow that runs a
 * small portable shell script on every push and pull request, failing the run when
 * the generated {@code CLAUDE.md} is missing or empty. Because the adoption
 * already targets GitHub repositories, a workflow is a guard every adopted
 * repository can run without introducing a build tool.
 *
 * <p>Both files are committed, so the guard is shared with every contributor. The
 * script is the single source of truth: the workflow invokes it and
 * {@link FallbackBuildSystem#verifyCommand(Path)} runs it locally, so the adoption
 * fails before the branch is pushed just as it would in CI. The two are installed
 * independently and neither is overwritten — the rule {@link AssetInstaller} applies
 * to every file the adoption writes — so a checkout that kept the workflow but lost
 * the script has it written back.
 */
public class WorkflowGuardInstaller {

	private static final Logger log = LogManager.getLogger(WorkflowGuardInstaller.class);

	static final String WORKFLOW_FILE = ".github/workflows/claude-md-guard.yml";
	static final String SCRIPT_FILE = ".github/claude-md-guard.sh";

	private static final String MARKER = "Added by claude-code-adopt";

	/**
	 * The marker and the script's path, substituted into the two templates rather than
	 * formatted in so both files are written with LF on every platform. The script is
	 * run by {@code sh}, which reads a CRLF shebang line as an interpreter named
	 * {@code /bin/sh\r} and refuses to run it, so LF here is the guard working rather
	 * than a line-ending preference; the workflow beside it is written the same way, so
	 * the pair a run adds is identical wherever the run happened.
	 */
	private static final String MARKER_TOKEN = "@marker@";

	private static final String SCRIPT_FILE_TOKEN = "@scriptFile@";

	private static final String WORKFLOW = """
			# @marker@: fail CI when CLAUDE.md is missing or empty.
			name: CLAUDE.md guard
			on: [push, pull_request]
			jobs:
			  claude-md-guard:
			    runs-on: ubuntu-latest
			    steps:
			      - uses: actions/checkout@v4
			      - name: Enforce CLAUDE.md
			        run: sh @scriptFile@
			""".replace(MARKER_TOKEN, MARKER).replace(SCRIPT_FILE_TOKEN, SCRIPT_FILE);

	private static final String SCRIPT = """
			#!/bin/sh
			# @marker@: fail when CLAUDE.md is missing or empty.
			if [ ! -f CLAUDE.md ] || ! grep -q '[^[:space:]]' CLAUDE.md; then
			  echo "CLAUDE.md is missing or empty" >&2
			  exit 1
			fi
			""".replace(MARKER_TOKEN, MARKER);

	private final AssetInstaller workflowInstaller = new AssetInstaller(WORKFLOW_FILE, WORKFLOW);
	private final AssetInstaller scriptInstaller = new AssetInstaller(SCRIPT_FILE, SCRIPT);

	/**
	 * @return {@code true} when either guard file was written, {@code false} when the
	 *         checkout already carried both and was left unchanged.
	 */
	/**
	 * Whether the checkout already carries this adoption's workflow and its script;
	 * read-only, for a verification. Both are required, because the workflow runs the
	 * script and either alone guards nothing.
	 */
	public boolean isInstalled(Path repositoryDirectory) {
		return isAdoptionWorkflow(repositoryDirectory.resolve(WORKFLOW_FILE))
				&& Files.isRegularFile(repositoryDirectory.resolve(SCRIPT_FILE));
	}

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
		if (!workflowWritten && !isAdoptionWorkflow(repositoryDirectory.resolve(WORKFLOW_FILE))) {
			log.warn("{} already exists and is not the adoption's workflow; left unchanged, so CI may not run {}",
					WORKFLOW_FILE, SCRIPT_FILE);
		}
	}

	/** A workflow this installer wrote carries {@value #MARKER}; anything else is the project's own. */
	private boolean isAdoptionWorkflow(Path workflowFile) {
		return Files.isRegularFile(workflowFile)
				&& AdoptionFiles.read(workflowFile, "workflow file").contains(MARKER);
	}
}

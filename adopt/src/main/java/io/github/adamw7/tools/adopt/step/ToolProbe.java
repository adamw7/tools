package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Decides whether an external tool can actually be run, by starting a probe
 * command and reading its exit code. Shared by {@link ToolchainStep}, which
 * checks the pipeline's own {@code git}/{@code claude}/{@code gh} before any work
 * begins, and {@link BuildToolchainStep}, which checks the adopted project's
 * build tool as soon as the clone reveals which one it is — so both report a
 * missing tool the same way.
 */
final class ToolProbe {

	private static final Logger log = LogManager.getLogger(ToolProbe.class);

	/**
	 * Package-visible so {@link BuildWrapper} builds its own probe with the same flag
	 * rather than a copy of it that could drift.
	 */
	static final String VERSION_FLAG = "--version";

	/**
	 * @return the tools whose {@code --version} probe could not be run or exited
	 *         non-zero, in the order they were given. Every tool is probed even
	 *         after one is found missing, so a single failure names all of the
	 *         absent tools at once.
	 */
	List<String> missingFrom(List<String> tools, Path directory, CommandRunner runner) {
		return tools.stream().filter(tool -> !found(tool, directory, runner)).toList();
	}

	private boolean found(String tool, Path directory, CommandRunner runner) {
		boolean installed = isInstalled(tool, directory, runner);
		if (installed) {
			log.info("Found required tool: {}", tool);
		}
		return installed;
	}

	/**
	 * @return whether the tool's {@code --version} probe runs and exits zero. The
	 *         probe's shape lives here so {@link ToolchainStep} and
	 *         {@link BuildToolchainStep} cannot drift apart on it.
	 */
	boolean isInstalled(String tool, Path directory, CommandRunner runner) {
		return succeeds(List.of(tool, VERSION_FLAG), directory, runner);
	}

	/**
	 * A command the runner cannot even start throws an {@link AdoptionException} out
	 * of {@link CommandRunner#run}; for a probe that is exactly the "not installed"
	 * answer being looked for, so it is caught and reported as a failed probe rather
	 * than aborting the whole check on the first absent tool.
	 */
	boolean succeeds(List<String> command, Path directory, CommandRunner runner) {
		try {
			return runner.run(directory, command).succeeded();
		} catch (AdoptionException e) {
			log.warn("Probe {} could not be run: {}", CommandResult.describe(command), e.getMessage());
			return false;
		}
	}
}

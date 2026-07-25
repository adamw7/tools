package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.ArrayList;
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

	private static final String VERSION_FLAG = "--version";

	/**
	 * @return the tools whose {@code --version} probe could not be run or exited
	 *         non-zero, in the order they were given. Every tool is probed even
	 *         after one is found missing, so a single failure can name all of the
	 *         absent tools at once rather than stopping at the first and hiding the
	 *         rest.
	 */
	List<String> missingFrom(List<String> tools, Path directory, CommandRunner runner) {
		List<String> missing = new ArrayList<>();
		for (String tool : tools) {
			collectIfMissing(missing, tool, directory, runner);
		}
		return missing;
	}

	private void collectIfMissing(List<String> missing, String tool, Path directory, CommandRunner runner) {
		if (succeeds(List.of(tool, VERSION_FLAG), directory, runner)) {
			log.info("Found required tool: {}", tool);
		} else {
			missing.add(tool);
		}
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

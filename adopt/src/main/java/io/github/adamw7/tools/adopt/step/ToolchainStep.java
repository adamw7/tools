package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Verifies up front that every external tool the pipeline shells out to is
 * available — {@code git} to clone and commit, {@code claude} to generate the
 * {@code CLAUDE.md}, and {@code gh} to open the pull request. Probing the
 * toolchain before any real work begins turns a missing {@code gh} into an
 * immediate, self-explanatory failure instead of one that only surfaces at the
 * very end, after a full clone, a {@code claude init}, and a Maven build have
 * already run.
 *
 * <p>A tool counts as available when its {@code --version} probe starts and exits
 * zero. Every required tool is probed even after one is found missing, so a
 * single failure can name all of the absent tools at once rather than stopping at
 * the first and hiding the rest.
 */
public class ToolchainStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(ToolchainStep.class);

	static final List<String> DEFAULT_TOOLS = List.of("git", "claude", "gh");

	private final List<String> tools;

	public ToolchainStep() {
		this(DEFAULT_TOOLS);
	}

	public ToolchainStep(List<String> tools) {
		this.tools = List.copyOf(tools);
	}

	@Override
	public String name() {
		return "toolchain";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		log.info("Checking required tools are available: {}", tools);
		List<String> missing = missingTools(context, runner);
		if (!missing.isEmpty()) {
			throw new AdoptionException(name() + " failed: required tools were not found on the PATH: "
					+ String.join(", ", missing));
		}
	}

	private List<String> missingTools(AdoptionContext context, CommandRunner runner) {
		List<String> missing = new ArrayList<>();
		for (String tool : tools) {
			collectIfMissing(missing, context, runner, tool);
		}
		return missing;
	}

	private void collectIfMissing(List<String> missing, AdoptionContext context, CommandRunner runner, String tool) {
		if (isAvailable(context, runner, tool)) {
			log.info("Found required tool: {}", tool);
		} else {
			missing.add(tool);
		}
	}

	/**
	 * A tool the runner cannot even start throws an {@link AdoptionException} out
	 * of {@link CommandRunner#run}; that is exactly the "not installed" case the
	 * probe is looking for, so it is caught and reported as unavailable rather than
	 * aborting the whole check on the first absent tool.
	 */
	private boolean isAvailable(AdoptionContext context, CommandRunner runner, String tool) {
		try {
			CommandResult result = runner.run(context.workspace(), List.of(tool, "--version"));
			return result.succeeded();
		} catch (AdoptionException e) {
			log.warn("Required tool {} could not be run: {}", tool, e.getMessage());
			return false;
		}
	}
}

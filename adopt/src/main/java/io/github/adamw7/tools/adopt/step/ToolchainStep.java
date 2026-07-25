package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
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
 * single failure names all of the absent tools at once.
 *
 * <p>Being installed is not enough for {@code gh}: {@code gh --version} succeeds
 * for a GitHub CLI nobody is logged in to, which the adoption would only discover
 * at {@link PullRequestStep}, its very last step. The login is therefore probed
 * here too. The adopted project's own build tool only becomes known once the
 * repository is cloned, so {@link BuildToolchainStep} probes it there.
 */
public class ToolchainStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(ToolchainStep.class);

	static final String GITHUB_CLI = "gh";
	static final List<String> DEFAULT_TOOLS = List.of("git", "claude", GITHUB_CLI);
	static final List<String> AUTHENTICATION_PROBE = List.of(GITHUB_CLI, "auth", "status");

	private final List<String> tools;
	private final ToolProbe probe = new ToolProbe();

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
		requireInstalled(context, runner);
		requireGitHubLogin(context, runner);
	}

	private void requireInstalled(AdoptionContext context, CommandRunner runner) {
		List<String> missing = probe.missingFrom(tools, context.workspace(), runner);
		if (!missing.isEmpty()) {
			throw new AdoptionException(name() + " failed: required tools were not found on the PATH: "
					+ String.join(", ", missing));
		}
	}

	private void requireGitHubLogin(AdoptionContext context, CommandRunner runner) {
		if (!tools.contains(GITHUB_CLI) || probe.succeeds(AUTHENTICATION_PROBE, context.workspace(), runner)) {
			return;
		}
		throw new AdoptionException(name() + " failed: " + GITHUB_CLI
				+ " is installed but not authenticated, so the pull request could not be opened."
				+ " Run 'gh auth login', or set GH_TOKEN for a non-interactive host.");
	}
}

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
 * {@code CLAUDE.md}, {@code gh} to open the pull request — so a missing one fails
 * immediately instead of at the very end, after a full clone, a
 * {@code claude init}, and a build have already run. A tool counts as available
 * when its {@code --version} probe starts and exits zero, and every required tool
 * is probed even after one is found missing, so a single failure names them all.
 *
 * <p>Being installed is not enough for {@code gh}: {@code gh --version} succeeds
 * for a GitHub CLI nobody is logged in to, which the adoption would only discover
 * at {@link PullRequestStep}. The login is therefore probed here too, by making a
 * call to GitHub rather than by asking {@code gh} what it has stored — see
 * {@link #authenticationProbe}. The adopted project's own build tool only becomes
 * known once the repository is cloned, so {@link BuildToolchainStep} probes it
 * there.
 *
 * <p>Which tools are required follows what the run will actually do: a dry run
 * needs neither {@code gh} nor the credentials behind it, and is checked with
 * {@link #forDryRun()}.
 */
public class ToolchainStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(ToolchainStep.class);

	static final String GITHUB_CLI = "gh";
	static final List<String> DEFAULT_TOOLS = List.of("git", "claude", GITHUB_CLI);

	/**
	 * What a dry run actually shells out to. A dry run's pipeline is assembled
	 * without {@link PushStep} and {@link PullRequestStep}, and {@code gh} is used
	 * by the pull request alone — {@link PushStep} pushes with {@code git} — so a
	 * rehearsal never calls it.
	 */
	static final List<String> DRY_RUN_TOOLS = List.of("git", "claude");

	/**
	 * The probe for a run whose URL names no owner, and so no repository to ask
	 * about. It answers the weaker question — whether the credentials belong to
	 * anyone — because there is nothing better to ask.
	 */
	private static final List<String> AUTHENTICATION_PROBE = List.of(GITHUB_CLI, "api", "user");

	private final List<String> tools;
	private final ToolProbe probe = new ToolProbe();

	public ToolchainStep() {
		this(DEFAULT_TOOLS);
	}

	public ToolchainStep(List<String> tools) {
		this.tools = List.copyOf(tools);
	}

	/**
	 * The check for a run that will not publish. Holding a rehearsal to the
	 * credentials of the one capability it has been told not to exercise failed
	 * {@code --dry-run} on its very first step, for want of a {@code gh} the run was
	 * never going to call — on a host with no GitHub CLI installed, and on a token
	 * that cannot read the repository, both of which leave the rest of the pipeline
	 * perfectly runnable. Dropping {@code gh} from the list also settles the login
	 * probe, which asks about GitHub only while the pull request is still ahead.
	 */
	public static ToolchainStep forDryRun() {
		return new ToolchainStep(DRY_RUN_TOOLS);
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
		if (!tools.contains(GITHUB_CLI)
				|| probe.succeeds(authenticationProbe(context), context.workspace(), runner)) {
			return;
		}
		throw new AdoptionException(name() + " failed: " + GITHUB_CLI + " is installed but cannot reach "
				+ context.repositorySlug().orElse("GitHub")
				+ " with the credentials it holds, so the pull request could not be opened."
				+ " Run 'gh auth login', or set GH_TOKEN to a token that can write to the repository.");
	}

	/**
	 * Asks about the repository the run is going to open a pull request on, rather
	 * than about the token's owner. {@code gh api user} is a user-scoped call, and a
	 * GitHub App installation token — what a CI run is handed, and the credential
	 * behind the {@code x-access-token:TOKEN@github.com} clone URLs the adoption is
	 * built to accept — is refused by it with "Resource not accessible by
	 * integration". Probing with it therefore aborted, on its very first step, every
	 * adoption driven by the CI credential that would have opened the pull request
	 * perfectly well.
	 *
	 * <p>Reading the repository is also the stronger question: it covers a token
	 * GitHub rejects outright and one that is valid but cannot see this repository,
	 * both of which stop {@link PullRequestStep}. {@code gh auth status} answers
	 * neither — it reports the credentials {@code gh} has stored and exits zero even
	 * while printing that GitHub rejected them.
	 */
	private List<String> authenticationProbe(AdoptionContext context) {
		return context.repositorySlug()
				.map(slug -> List.of(GITHUB_CLI, "api", "repos/" + slug))
				.orElse(AUTHENTICATION_PROBE);
	}
}

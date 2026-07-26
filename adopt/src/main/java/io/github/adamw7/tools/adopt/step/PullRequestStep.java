package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionReport;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Opens a pull request for the adoption feature branch with the GitHub CLI
 * ({@code gh pr create}), targeting the repository's default branch as the base.
 * The pull request metadata — title, body, reviewers, labels, assignees, and
 * whether it is a draft — is supplied through {@link PullRequestOptions} because
 * it differs between projects; the defaults describe the Claude Code adoption
 * and request nobody.
 *
 * <p>The step stays idempotent when re-run: it asks {@code gh pr list --state
 * open} whether an <em>open</em> pull request already exists for the branch and
 * skips creation when one does, rather than matching the wording of a failure.
 * Scoping the query to open pull requests matters because a branch whose earlier
 * pull request was closed or merged still needs a fresh one.
 *
 * <p>Both commands name the target repository with {@code --repo} rather than
 * letting {@code gh} infer it from the checkout's git remote, which an
 * {@code insteadOf} rewrite, an organisation mirror, or a proxied clone can leave
 * unreadable as a GitHub one — failing the very last step of an otherwise
 * complete adoption. A URL that names no owner leaves the flag off, so {@code gh}
 * falls back to its own inference.
 *
 * <p>The pull request's URL is recorded in the run's {@link AdoptionReport}, read
 * back with {@code gh pr list --json url} rather than scraped from {@code gh pr
 * create}'s human-oriented output, so both the fresh and the re-run case take one
 * structured path.
 */
public class PullRequestStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(PullRequestStep.class);

	/**
	 * The conditions {@code gh pr create} reports as a failure that mean the pull
	 * request need not — or cannot — be opened, matched against its own wording:
	 * "a pull request for branch ... already exists" and "No commits between ...".
	 */
	static final List<String> TOLERATED_FAILURES = List.of("already exists", "no commits between");

	private final PullRequestOptions options;
	private final ObjectMapper mapper = new ObjectMapper();

	public PullRequestStep() {
		this(PullRequestOptions.defaults());
	}

	public PullRequestStep(PullRequestOptions options) {
		this.options = options;
	}

	@Override
	public String name() {
		return "pull-request";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		execute(context, runner, new AdoptionReport());
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner, AdoptionReport report) {
		Optional<String> existing = openPullRequestUrl(context, runner);
		if (existing.isPresent()) {
			log.info("Pull request already open for branch {}; left unchanged", context.branchName());
			record(existing.get(), report);
		} else {
			create(context, runner);
			recordUrl(context, runner, report);
		}
	}

	/**
	 * A {@code gh pr create} that fails only because there is nothing to open —
	 * the branch already has an open pull request, or it carries no commits the base
	 * does not — leaves the adoption complete rather than aborting it at its last
	 * step. Both are the normal outcome of re-running an adoption that already
	 * finished: the first is what {@link #openPullRequestUrl} would have caught had
	 * {@code gh} been queryable, and the second is what an adoption of an
	 * already-adopted repository produces, every step having found its work done.
	 */
	private void create(AdoptionContext context, CommandRunner runner) {
		log.info("Opening pull request for branch {}", context.branchName());
		runTolerating(runner, context.repositoryDirectory(), createCommand(context), TOLERATED_FAILURES)
				.ifPresentOrElse(
						result -> log.info("Opened pull request: {}", result.output().strip()),
						() -> log.info("Nothing to open a pull request for on branch {}; left unchanged",
								context.branchName()));
	}

	private List<String> createCommand(AdoptionContext context) {
		List<String> command = new ArrayList<>(List.of("gh", "pr", "create", "--title", options.title(), "--body",
				options.body(), "--head", context.branchName()));
		addTargetRepository(command, context);
		if (options.draft()) {
			command.add("--draft");
		}
		addRepeated(command, "--reviewer", options.reviewers());
		addRepeated(command, "--label", options.labels());
		addRepeated(command, "--assignee", options.assignees());
		return List.copyOf(command);
	}

	private void addTargetRepository(List<String> command, AdoptionContext context) {
		context.repositorySlug().ifPresent(slug -> command.addAll(List.of("--repo", slug)));
	}

	private void addRepeated(List<String> command, String flag, List<String> values) {
		values.forEach(value -> command.addAll(List.of(flag, value)));
	}

	/**
	 * A query that fails is reported rather than passed off as "no pull request is
	 * open": the two are indistinguishable in the return value, and a caller reading
	 * the log of an adoption that went on to create a duplicate needs to see which
	 * of the two it was.
	 *
	 * @return the URL of the branch's open pull request, or empty when none is open
	 *         or {@code gh} could not be queried
	 */
	private Optional<String> openPullRequestUrl(AdoptionContext context, CommandRunner runner) {
		CommandResult result = runner.run(context.repositoryDirectory(), listCommand(context));
		if (!result.succeeded()) {
			log.warn("Could not ask gh which pull requests are open for branch {} (exit {}): {}",
					context.branchName(), result.exitCode(), result.output().strip());
			return Optional.empty();
		}
		return extractUrl(result.output());
	}

	private List<String> listCommand(AdoptionContext context) {
		List<String> command = new ArrayList<>(List.of("gh", "pr", "list", "--head", context.branchName(), "--state",
				"open", "--json", "url"));
		addTargetRepository(command, context);
		return List.copyOf(command);
	}

	/**
	 * A pull request that was just created must be listable, so a failing read-back
	 * is a warning rather than an aborted adoption: the pull request itself exists
	 * and only its URL is missing from the report.
	 */
	private void recordUrl(AdoptionContext context, CommandRunner runner, AdoptionReport report) {
		openPullRequestUrl(context, runner).ifPresentOrElse(
				url -> record(url, report),
				() -> log.warn("Could not read back the pull request URL for branch {}", context.branchName()));
	}

	private void record(String url, AdoptionReport report) {
		log.info("Pull request URL: {}", url);
		report.recordPullRequestUrl(url);
	}

	/**
	 * {@code gh pr list --json} writes a JSON array to stdout, but the captured
	 * output may carry surrounding noise (update notices merged in from stderr), so
	 * parsing starts at an opening bracket. Every bracket is tried rather than only
	 * the first, because a diagnostic printed <em>before</em> the payload can itself
	 * contain one; stopping there would parse the noise, conclude no pull request is
	 * open, and make the step create a duplicate {@code gh} rejects — failing an
	 * adoption that was only being re-run.
	 *
	 * @return the first element's {@code url}, or empty when no bracket starts a
	 *         JSON array, the array is empty, or it carries no textual URL
	 */
	private Optional<String> extractUrl(String output) {
		for (int start = output.indexOf('['); start >= 0; start = output.indexOf('[', start + 1)) {
			Optional<String> url = firstUrl(output.substring(start));
			if (url.isPresent()) {
				return url;
			}
		}
		return Optional.empty();
	}

	private Optional<String> firstUrl(String json) {
		try {
			JsonNode array = mapper.readTree(json);
			if (!array.isArray() || array.isEmpty()) {
				return Optional.empty();
			}
			JsonNode url = array.get(0).path("url");
			return url.isTextual() ? Optional.of(url.asText()) : Optional.empty();
		} catch (JsonProcessingException e) {
			log.debug("Skipping a '[' in the gh pr list output that does not start a JSON array", e);
			return Optional.empty();
		}
	}
}

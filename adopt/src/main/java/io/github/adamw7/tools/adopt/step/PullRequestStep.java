package io.github.adamw7.tools.adopt.step;

import java.util.ArrayList;
import java.util.List;

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
 * <p>The step stays idempotent when re-run: it asks {@code gh pr view} whether a
 * pull request already exists for the branch and skips creation when one does,
 * rather than creating unconditionally and then matching the wording of a
 * failure. That keeps the decision robust across {@code gh} versions and locales.
 *
 * <p>After the pull request is created (or found already open), the step records
 * its URL in the run's {@link AdoptionReport}. The URL is read back with
 * {@code gh pr view --json url} rather than scraped from {@code gh pr create}'s
 * human-oriented output, so the extraction is one structured path for both the
 * fresh and the re-run case.
 */
public class PullRequestStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(PullRequestStep.class);

	private final PullRequestOptions options;
	private final ObjectMapper mapper = new ObjectMapper();

	public PullRequestStep() {
		this(PullRequestOptions.defaults());
	}

	public PullRequestStep(String title, String body) {
		this(PullRequestOptions.builder().title(title).body(body).build());
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
		if (pullRequestExists(context, runner)) {
			log.info("Pull request already open for branch {}; left unchanged", context.branchName());
		} else {
			create(context, runner);
		}
		recordUrl(context, runner, report);
	}

	private void create(AdoptionContext context, CommandRunner runner) {
		log.info("Opening pull request for branch {}", context.branchName());
		CommandResult result = runOrFail(runner, context.repositoryDirectory(), createCommand(context));
		log.info("Opened pull request: {}", result.output().strip());
	}

	private List<String> createCommand(AdoptionContext context) {
		List<String> command = new ArrayList<>(List.of("gh", "pr", "create", "--title", options.title(), "--body",
				options.body(), "--head", context.branchName()));
		if (options.draft()) {
			command.add("--draft");
		}
		addRepeated(command, "--reviewer", options.reviewers());
		addRepeated(command, "--label", options.labels());
		addRepeated(command, "--assignee", options.assignees());
		return List.copyOf(command);
	}

	private void addRepeated(List<String> command, String flag, List<String> values) {
		for (String value : values) {
			command.add(flag);
			command.add(value);
		}
	}

	private boolean pullRequestExists(AdoptionContext context, CommandRunner runner) {
		return runner.run(context.repositoryDirectory(), viewCommand(context)).succeeded();
	}

	private List<String> viewCommand(AdoptionContext context) {
		return List.of("gh", "pr", "view", context.branchName(), "--json", "url");
	}

	/**
	 * A pull request that was just created must be viewable, so a failing view is a
	 * warning rather than an aborted adoption: the pull request itself exists and
	 * only its URL is missing from the report.
	 */
	private void recordUrl(AdoptionContext context, CommandRunner runner, AdoptionReport report) {
		CommandResult result = runner.run(context.repositoryDirectory(), viewCommand(context));
		if (result.succeeded()) {
			recordUrlFrom(result.output(), report);
		} else {
			log.warn("Could not read back the pull request URL for branch {}", context.branchName());
		}
	}

	private void recordUrlFrom(String output, AdoptionReport report) {
		String url = extractUrl(output);
		if (url == null) {
			log.warn("gh pr view returned no URL in: {}", output.strip());
		} else {
			log.info("Pull request URL: {}", url);
			report.recordPullRequestUrl(url);
		}
	}

	/**
	 * {@code gh} writes the JSON document to stdout but the captured output may
	 * carry surrounding noise (for example update notices on stderr), so parsing
	 * starts at the first opening brace instead of assuming a pure JSON payload.
	 */
	private String extractUrl(String output) {
		int start = output.indexOf('{');
		if (start < 0) {
			return null;
		}
		return urlField(output.substring(start));
	}

	private String urlField(String json) {
		try {
			JsonNode url = mapper.readTree(json).path("url");
			return url.isTextual() ? url.asText() : null;
		} catch (JsonProcessingException e) {
			log.warn("Could not parse gh pr view output as JSON", e);
			return null;
		}
	}
}

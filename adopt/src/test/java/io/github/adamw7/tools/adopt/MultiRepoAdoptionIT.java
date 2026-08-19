package io.github.adamw7.tools.adopt;

import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
import io.github.adamw7.tools.adopt.command.RetryingCommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.BranchStep;
import io.github.adamw7.tools.adopt.step.CloneStep;
import io.github.adamw7.tools.adopt.step.ToolchainStep;

/**
 * Adopts several repositories in one run against <em>real</em> GitHub URLs, over
 * the network, with the real {@code git} the pipeline shells out to. The
 * unit tests drive the batch with a recording stub and synthetic URLs such as
 * {@code https://github.com/owner/repo.git}, which proves the wiring but cannot
 * prove that two real repositories end up as two real checkouts — the failure
 * that {@link Checkouts} exists to prevent is only visible once {@code git clone}
 * has actually run twice into one workspace.
 *
 * <p>The run goes through the command line the same way an operator's does —
 * {@link CliArguments} parses {@code --repo}/{@code --repos}, {@link Main} builds
 * the contexts and writes the report — so the multi-repository path is exercised
 * end to end rather than from {@link BatchAdoption} inwards.
 *
 * <p>What the pipeline is <em>not</em> given here is every step: the adoption
 * ends at {@link BranchStep}, so the run clones and branches for real but never
 * runs {@code claude}, never marks a checkout trusted, and above all never pushes
 * or opens a pull request. A test that ran the default pipeline would write to
 * repositories nobody asked it to write to, and would need a logged-in {@code gh}
 * to do it; the steps kept here are the ones the repository list actually changes
 * the behaviour of, and they only read from GitHub.
 *
 * <p>The repositories are GitHub's own long-lived samples, which are tiny — a few
 * hundred kilobytes each — so a clone costs about a second.
 */
class MultiRepoAdoptionIT {

	private static final String HELLO_WORLD = "https://github.com/octocat/Hello-World.git";
	private static final String SPOON_KNIFE = "https://github.com/octocat/Spoon-Knife.git";

	/** A well-formed GitHub URL for a repository that is not there, so the clone fails. */
	private static final String MISSING = "https://github.com/octocat/no-such-repository-tools-adopt-it.git";

	/** The SSH form of {@link #HELLO_WORLD}: a second real URL for one repository. */
	private static final String HELLO_WORLD_OVER_SSH = "git@github.com:octocat/Hello-World.git";

	private static final String BRANCH = "claude/adopt-it";

	/**
	 * Well under the runner's ten-minute default, which is sized for a
	 * {@code claude init} rather than for cloning a sample repository, so a stalled
	 * network fails the test in minutes instead of hanging the build.
	 */
	private final CommandRunner runner = new RetryingCommandRunner(
			new ProcessCommandRunner(Duration.ofMinutes(2)), AdoptionOptions.DEFAULT_RETRIES);

	private final ObjectMapper mapper = new ObjectMapper();

	@TempDir
	Path workspace;

	@TempDir
	Path reports;

	/**
	 * The batch's whole point: two repositories named on one command line become two
	 * independent checkouts. Asserting each checkout's {@code origin} — rather than
	 * only that two directories exist — is what catches a second adoption that
	 * silently reused the first repository's working tree, since the clone step
	 * reuses an existing checkout by design.
	 */
	@Test
	void adoptsTwoRealRepositoriesIntoTheirOwnCheckouts() throws IOException {
		Path report = reports.resolve("batch.json");
		CliArguments cli = parse("--repo", HELLO_WORLD, "--repo", SPOON_KNIFE,
				"--workspace", workspace.toString(), "--branch", BRANCH, "--report", report.toString());

		List<AdoptionRun> runs = Main.runAndReport(cli, Main.checkouts(cli), cloneOnlyAdopter());

		assertEquals(List.of(HELLO_WORLD, SPOON_KNIFE), runs.stream().map(AdoptionRun::repositoryUrl).toList());
		assertTrue(runs.stream().allMatch(AdoptionRun::succeeded), () -> failures(runs));
		assertCheckedOut(HELLO_WORLD);
		assertCheckedOut(SPOON_KNIFE);
		assertBatchReport(report, true, List.of(HELLO_WORLD, SPOON_KNIFE));
	}

	/**
	 * A repository that cannot be cloned is the failure an operator running a long
	 * list actually hits — a typo, a repository that moved, one they have no access
	 * to. It must cost them that repository and no other, so the run is driven with
	 * the missing one in the middle: a batch that stopped at the first failure would
	 * leave the third repository unadopted.
	 */
	@Test
	void adoptsTheRestOfTheListWhenOneRepositoryIsNotThere() throws IOException {
		Path report = reports.resolve("partial.json");
		CliArguments cli = parse("--repos", listFile(HELLO_WORLD, MISSING, SPOON_KNIFE).toString(),
				"--workspace", workspace.toString(), "--branch", BRANCH, "--report", report.toString());
		assertFailure(AdoptionException.class, () -> Main.runAndReport(cli, Main.checkouts(cli), cloneOnlyAdopter()),
				"1 of 3 repositories", MISSING);
		assertCheckedOut(HELLO_WORLD);
		assertCheckedOut(SPOON_KNIFE);
		assertFalse(Files.exists(workspace.resolve("no-such-repository-tools-adopt-it")),
				"the repository that could not be cloned must leave no checkout behind");
		assertBatchReport(report, false, List.of(HELLO_WORLD, MISSING, SPOON_KNIFE));
		assertCloneFailedFor(report, MISSING);
	}

	/**
	 * GitHub offers a repository's HTTPS and SSH URLs side by side, so a list
	 * assembled by pasting from both names one repository twice in two forms. They
	 * are not duplicates as text, and they claim one checkout directory — so the
	 * second is refused rather than adopting the repository twice. The refusal costs
	 * that URL alone: the first is cloned and adopted as asked.
	 */
	@Test
	void refusesTheSecondOfTwoRealUrlsForOneRepository() throws IOException {
		CliArguments cli = parse("--repo", HELLO_WORLD, "--repo", HELLO_WORLD_OVER_SSH,
				"--workspace", workspace.toString(), "--branch", BRANCH);

		assertFailure(AdoptionException.class, () -> Main.runAndReport(cli, Main.checkouts(cli), cloneOnlyAdopter()),
				"1 of 2 repositories", HELLO_WORLD_OVER_SSH, workspace.resolve("Hello-World").toString());
		assertCheckedOut(HELLO_WORLD);
	}

	/**
	 * A malformed URL in a list an operator assembled by hand is the other failure
	 * they hit, and it used to abort the run before its first clone — leaving every
	 * repository behind it unadopted and no report to say so.
	 */
	@Test
	void adoptsTheRestOfTheListWhenOneUrlNamesNoRepository() throws IOException {
		Path report = reports.resolve("malformed.json");
		CliArguments cli = parse("--repos", listFile(HELLO_WORLD, "https://github.com/owner/.git", SPOON_KNIFE).toString(),
				"--workspace", workspace.toString(), "--branch", BRANCH, "--report", report.toString());

		assertFailure(AdoptionException.class, () -> Main.runAndReport(cli, Main.checkouts(cli), cloneOnlyAdopter()),
				"1 of 3 repositories");
		assertCheckedOut(HELLO_WORLD);
		assertCheckedOut(SPOON_KNIFE);
		assertBatchReport(report, false, List.of(HELLO_WORLD, "https://github.com/owner/.git", SPOON_KNIFE));
	}

	/**
	 * Within one run {@link Checkouts} refuses the second claim on a checkout
	 * directory, but two runs into one named workspace never meet — and a checkout
	 * directory is named after the repository alone, while one repository name
	 * belongs to many owners. Only the clone step's own check then stands between
	 * the operator and an adoption that branches, commits, and pushes a repository
	 * nobody asked it to touch, so the collision is staged the way it arises: a real
	 * checkout of one repository sitting under the directory name the next run's
	 * repository claims.
	 */
	@Test
	void refusesACheckoutDirectoryHoldingADifferentRepository() {
		Path checkout = workspace.resolve("Spoon-Knife");
		git(workspace, "clone", HELLO_WORLD, checkout.toString());
		CliArguments cli = parse("--repo", SPOON_KNIFE, "--workspace", workspace.toString(), "--branch", BRANCH);

		assertFailure(AdoptionException.class, () -> Main.runAndReport(cli, Main.checkouts(cli), cloneOnlyAdopter()),
				checkout.toString(), "Hello-World");
		assertTrue(git(checkout, "remote", "get-url", "origin").endsWith("Hello-World.git"),
				"the checkout of another repository must be left as it was found");
		assertFalse(BRANCH.equals(git(checkout, "rev-parse", "--abbrev-ref", "HEAD")),
				"the adoption branch must never be created in another repository's checkout");
	}

	/**
	 * The pipeline stops at the branch step: the adoption stays read-only towards
	 * GitHub, so this test can be run against real repositories as often as it likes.
	 * The toolchain step is given {@code git} alone for the same reason — the
	 * {@code claude} and {@code gh} it normally requires belong to the steps that are
	 * not here.
	 */
	private GitHubRepoAdopter cloneOnlyAdopter() {
		List<AdoptionStep> steps = List.of(new ToolchainStep(List.of("git")), new CloneStep(), new BranchStep());
		return new GitHubRepoAdopter(runner, steps);
	}

	/**
	 * Every command line here keeps its checkouts, because inspecting them is what
	 * these tests do: an ordinary run removes the checkout of an adoption that landed,
	 * its product being the pushed branch and the pull request.
	 */
	private CliArguments parse(String... arguments) {
		String[] keepingCheckouts = Arrays.copyOf(arguments, arguments.length + 1);
		keepingCheckouts[arguments.length] = "--keep-workspace";
		return CliArguments.parse(keepingCheckouts);
	}

	/** A {@code --repos} file as an operator writes one: a comment, then the URLs. */
	private Path listFile(String... urls) throws IOException {
		Path file = reports.resolve("repositories.txt");
		return Files.writeString(file, "# the batch under test\n" + String.join("\n", urls) + "\n");
	}

	/**
	 * Identifies the checkout by the {@code owner/repository} its {@code origin}
	 * names, rather than by the URL the run was given: a host may carry a git
	 * {@code url.<base>.insteadOf} rewrite — a mirror, a caching proxy — and the
	 * remote then records the rewritten URL. The slug survives that rewrite and is
	 * the fact under test anyway, since two checkouts of one repository is exactly
	 * what would go wrong.
	 */
	private void assertCheckedOut(String repositoryUrl) {
		RepositoryUrl repository = RepositoryUrl.of(repositoryUrl);
		String slug = repository.slug().orElseThrow();
		Path checkout = workspace.resolve(repository.name());
		assertTrue(Files.isDirectory(checkout.resolve(".git")), () -> checkout + " is not a git checkout");
		String origin = git(checkout, "remote", "get-url", "origin");
		assertTrue(origin.endsWith(slug) || origin.endsWith(slug + ".git"),
				() -> checkout + " was cloned from " + origin + ", which is not " + slug);
		assertEquals(BRANCH, git(checkout, "rev-parse", "--abbrev-ref", "HEAD"));
	}

	private void assertBatchReport(Path report, boolean succeeded, List<String> repositoryUrls) throws IOException {
		JsonNode document = mapper.readTree(Files.readString(report));
		assertEquals(succeeded, document.get("succeeded").asBoolean());
		assertEquals(repositoryUrls, document.get("repositories").findValuesAsText("repositoryUrl"));
	}

	/**
	 * The report must say which repository stopped and where, not merely that the
	 * batch was not wholly successful, so the entry naming the missing repository is
	 * the one that has to carry the failure — and it has to blame the clone.
	 */
	private void assertCloneFailedFor(Path report, String repositoryUrl) throws IOException {
		JsonNode repository = repositoryIn(report, repositoryUrl);
		assertFalse(repository.get("succeeded").asBoolean());
		assertTrue(repository.get("failure").asText().startsWith("clone: "), repository.get("failure").asText());
	}

	private JsonNode repositoryIn(Path report, String repositoryUrl) throws IOException {
		JsonNode repositories = mapper.readTree(Files.readString(report)).get("repositories");
		return repositories.valueStream()
				.filter(entry -> repositoryUrl.equals(entry.get("repositoryUrl").asText()))
				.findFirst()
				.orElseThrow(() -> new AssertionError(repositoryUrl + " is missing from " + repositories));
	}

	private String git(Path directory, String... arguments) {
		List<String> command = Stream.concat(Stream.of("git"), Arrays.stream(arguments)).toList();
		CommandResult result = runner.run(directory, command);
		assertTrue(result.succeeded(), () -> result.describe() + " failed: " + result.output());
		return result.output().strip();
	}

	private String failures(List<AdoptionRun> runs) {
		return runs.stream().map(run -> run.repositoryUrl() + ": " + run.failure().orElse("ok")).toList().toString();
	}
}

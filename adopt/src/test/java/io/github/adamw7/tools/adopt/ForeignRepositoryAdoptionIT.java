package io.github.adamw7.tools.adopt;

import static io.github.adamw7.tools.test.TestFiles.readString;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionAssets;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.BranchStep;
import io.github.adamw7.tools.adopt.step.BuildSystem;
import io.github.adamw7.tools.adopt.step.BuildSystems;
import io.github.adamw7.tools.adopt.step.ClaudeMdConformer;
import io.github.adamw7.tools.adopt.step.CloneStep;
import io.github.adamw7.tools.adopt.step.CommitStep;
import io.github.adamw7.tools.adopt.step.EnforcerStep;
import io.github.adamw7.tools.adopt.step.ToolchainStep;
import io.github.adamw7.tools.enforcer.doc.ClaudeMdFormatRule;

/**
 * Adopts five <em>real</em> repositories, cloned from GitHub over the network, none
 * of which has ever heard of this pipeline.
 *
 * <p>{@link MultiRepoAdoptionIT} proves a batch gives each repository its own
 * checkout, and every step test drives its step with a recording runner over a
 * directory this repository laid out. Both work on inputs written to be adopted: a
 * fixture carries the build file the installer expects, in the shape it expects it,
 * so breaking one thing proves one behaviour. Neither answers what the adoption does
 * to a project nobody prepared for it — which is every project it will ever be
 * pointed at.
 *
 * <p>The five are chosen for the shapes they put in front of the steps that read the
 * checkout: a real multi-module Maven build, a real Gradle build on the Kotlin DSL, a
 * real {@code CLAUDE.md}, a real {@code .claude} directory, and a very large flat tree
 * with no build file at all. Between them they exercise all three
 * {@link BuildSystem}s on build files this repository did not write.
 *
 * <p>What is asserted is that the adoption's work on each of them is <em>its own and
 * nothing else</em>: the guard that lands is the one the checkout's build files ask
 * for, the commit carries only paths {@link AdoptionAssets#WRITTEN_PATHS} names,
 * nothing the project already declared is removed to make room for it, the default
 * branch and the remote are left exactly as they were cloned, and adopting the same
 * repository a second time changes nothing.
 *
 * <p>The pipeline is cut short of {@link io.github.adamw7.tools.adopt.step.PushStep}
 * and the pull request, as {@link MultiRepoAdoptionIT}'s is, so the run stays
 * read-only towards GitHub and may be repeated as often as the integration suite
 * likes. It also leaves out the three steps that would cost more than they prove
 * here: {@code claude init}, which needs a logged-in {@code claude} and would write a
 * document nothing could then predict; the build-toolchain probe and the
 * verification, which for a checkout shipping a wrapper mean downloading that
 * project's whole build tool. What the guard does once it runs is the enforcer
 * module's own integration tests to say.
 */
class ForeignRepositoryAdoptionIT {

	/**
	 * A repository worth adopting, and what makes it worth adopting — the shape is
	 * quoted into every assertion message, so a failure says which kind of project
	 * defeated the pipeline rather than only which URL.
	 *
	 * @param url         the clone URL, over HTTPS so no key is needed
	 * @param shape       what this repository holds that a fixture cannot imitate
	 * @param buildSystem the {@link BuildSystem#name()} its own files call for
	 * @param guardPaths  the checkout-relative paths adopting it must commit, sorted
	 */
	private record RealRepository(String url, String shape, String buildSystem, List<String> guardPaths) {

		String name() {
			return RepositoryUrl.of(url).name();
		}

		@Override
		public String toString() {
			return name() + " (" + shape + ")";
		}
	}

	private static final String WORKFLOW_GUARD = ".github/workflows/claude-md-guard.yml";
	private static final String GUARD_SCRIPT = ".github/claude-md-guard.sh";

	/** What the {@code github-actions} fallback writes, in the order git reports them. */
	private static final List<String> WORKFLOW_GUARD_PATHS = List.of(GUARD_SCRIPT, WORKFLOW_GUARD);

	private static final String GITHUB_ACTIONS = "github-actions";

	private static final List<RealRepository> REPOSITORIES = List.of(
			new RealRepository("https://github.com/google/gson.git",
					"a real multi-module Maven build, so the enforcer rule is spliced into a pom.xml"
							+ " somebody else wrote",
					"maven", List.of("pom.xml")),
			new RealRepository("https://github.com/square/okhttp.git",
					"a real Gradle build on the Kotlin DSL, whose guard task is appended to a build script"
							+ " somebody else wrote",
					"gradle", List.of("build.gradle.kts")),
			new RealRepository("https://github.com/anthropics/anthropic-quickstarts.git",
					"a real CLAUDE.md, written by someone who never heard of this pipeline",
					GITHUB_ACTIONS, WORKFLOW_GUARD_PATHS),
			new RealRepository("https://github.com/anthropics/claude-code.git",
					"a real .claude directory somebody else wrote, and no build file to wire a guard into",
					GITHUB_ACTIONS, WORKFLOW_GUARD_PATHS),
			new RealRepository("https://github.com/github/gitignore.git",
					"thousands of small files in one flat tree, and no build file",
					GITHUB_ACTIONS, WORKFLOW_GUARD_PATHS));

	/** The repository whose {@code CLAUDE.md} somebody else wrote. */
	private static final RealRepository WITH_CLAUDE_MD = named("anthropic-quickstarts");

	private static final String CLAUDE_MD = "CLAUDE.md";

	private static final String BRANCH = "claude/adopt-it";

	/**
	 * The released rule version the Maven guard pins, supplied here the way
	 * {@code --rule-version} supplies it to an operator adopting from a checkout: a
	 * development build resolves its own {@code -SNAPSHOT}, and the installer refuses
	 * to wire that into somebody else's {@code pom.xml}, since a snapshot resolves from
	 * this machine's local repository and nowhere else. Nothing resolves this one
	 * either — no adopted project is built in this class — so it has only to be a
	 * release, and it names the latest one this repository published.
	 */
	private static final String RULE_VERSION = "2.5.0";

	/** The one list the guard is installed from and the expectations are detected with. */
	private static final List<BuildSystem> BUILD_SYSTEMS = BuildSystems.defaults(Optional.of(RULE_VERSION));

	/** The steps this run is given, in order, as each reports itself to the report. */
	private static final List<String> PIPELINE = List.of("toolchain", "clone", "branch", "enforcer", "commit:guard");

	/**
	 * Generous next to the second or two a clone of these repositories costs, and short
	 * enough that a stalled network fails the class in minutes. The pipeline's own
	 * default is sized for a {@code claude init}, which is not among the steps here.
	 */
	private static final CommandRunner RUNNER = new ProcessCommandRunner(Duration.ofMinutes(5));

	/**
	 * Class-scoped, because the five clones are what this class costs and every test
	 * below reads the same checkouts.
	 */
	@TempDir
	static Path workspace;

	private static List<AdoptionRun> runs;

	@TempDir
	Path conformed;

	@BeforeAll
	static void adoptEveryRepository() {
		runs = adoptAll();
	}

	/**
	 * The claim this class exists for: the pipeline runs to its end on a project nobody
	 * prepared for it. A step that stopped — a build file it could not read, a checkout
	 * whose shape it did not expect — leaves an adopter a failed run on their own
	 * repository, and the report is what says how far it got.
	 */
	@Test
	void everyRealRepositoryIsAdoptedToTheEndOfThePipeline() {
		assertEquals(REPOSITORIES.stream().map(RealRepository::url).toList(),
				runs.stream().map(AdoptionRun::repositoryUrl).toList());
		for (int index = 0; index < REPOSITORIES.size(); index++) {
			AdoptionRun run = runs.get(index);
			RealRepository repository = REPOSITORIES.get(index);
			assertTrue(run.succeeded(),
					() -> "adopting " + repository + " failed: " + run.failure().orElse("no failure recorded"));
			assertEquals(PIPELINE, run.report().completedSteps(), () -> "adopting " + repository);
		}
	}

	/**
	 * Which guard lands is the checkout's own build files to decide, and these five are
	 * the only place that decision is taken over build files this repository did not
	 * write. A pipeline that read a real Gradle project as having no build system would
	 * commit a GitHub Actions workflow to it and call the adoption complete.
	 */
	@Test
	void theGuardWiredIntoEachRepositoryIsTheOneItsBuildFilesAskFor() {
		for (RealRepository repository : REPOSITORIES) {
			BuildSystem detected = BuildSystems.detect(BUILD_SYSTEMS, checkoutOf(repository))
					.orElseThrow(() -> new AssertionError("No build system matched " + repository));
			assertEquals(repository.buildSystem(), detected.name(), () -> "adopting " + repository);
			assertEquals(repository.guardPaths(), committedPaths(repository),
					() -> "the guard commit in " + repository + " is not the one " + detected.name() + " installs");
		}
	}

	/**
	 * The adoption arrives in somebody else's repository and commits there, so what it
	 * commits has to be its own. Every path is held to {@link AdoptionAssets#WRITTEN_PATHS},
	 * which is the list {@link CloneStep} tells the adoption's work apart with and
	 * {@link CommitStep} refuses an ignored path over — a file outside it is one the
	 * adoption cannot account for, and here it would be one of the project's.
	 *
	 * <p>The working tree is asserted clean for the other half of the same claim: a file
	 * the adoption wrote and did not commit would be left behind in the contributor's
	 * checkout, and one of the project's own files quietly modified would show up here
	 * rather than in a diff nobody reads.
	 */
	@Test
	void theAdoptionCommitsOnlyTheFilesItOwnsAndLeavesNothingBehind() {
		for (RealRepository repository : REPOSITORIES) {
			Path checkout = checkoutOf(repository);
			assertEquals(GitHubRepoAdopter.GUARD_COMMIT_MESSAGE, git(checkout, "log", "-1", "--format=%s"),
					() -> "the tip of " + BRANCH + " in " + repository + " is not the adoption's guard commit");
			for (String path : committedPaths(repository)) {
				assertTrue(AdoptionAssets.WRITTEN_PATHS.contains(path),
						() -> "adopting " + repository + " committed " + path + ", which is not a path the adoption"
								+ " writes: " + AdoptionAssets.WRITTEN_PATHS);
			}
			assertEquals("", git(checkout, "status", "--porcelain", "--untracked-files=all"),
					() -> "adopting " + repository + " left work behind in the checkout");
		}
	}

	/**
	 * A guard wired into a build file somebody else wrote must be an addition to it.
	 * Every one of these build files declares things the project depends on — gson's
	 * modules, okhttp's plugins — and an installer that reformatted, reordered, or
	 * dropped any of them to fit its own block in would offer that as part of adopting
	 * Claude Code. Reading the commit's diff rather than the file afterwards is what
	 * makes the claim checkable without a copy of each project's build file here.
	 */
	@Test
	void theGuardIsAddedWithoutRemovingWhatTheBuildFileAlreadyDeclared() {
		for (RealRepository repository : REPOSITORIES) {
			List<String> removals = git(checkoutOf(repository), "show", "--unified=0", "--pretty=format:", "HEAD")
					.lines()
					.filter(line -> line.startsWith("-") && !line.startsWith("--- "))
					.toList();
			assertTrue(removals.isEmpty(),
					() -> "adopting " + repository + " removed lines its build files already carried: " + removals);
		}
	}

	/**
	 * The promise the adoption makes to a repository it is pointed at: the default
	 * branch is never written to, and nothing leaves the machine until an operator
	 * pushes. Both are asserted against the refs {@code git clone} produced, so a step
	 * that committed on the wrong branch — or a pipeline that grew a push — is caught
	 * here rather than on somebody's repository.
	 */
	@Test
	void neitherTheDefaultBranchNorTheRemoteIsWrittenTo() {
		for (RealRepository repository : REPOSITORIES) {
			Path checkout = checkoutOf(repository);
			String defaultBranch = defaultBranchOf(checkout);
			assertEquals(BRANCH, git(checkout, "rev-parse", "--abbrev-ref", "HEAD"), () -> "adopting " + repository);
			assertEquals(git(checkout, "rev-parse", AdoptionContext.REMOTE + "/" + defaultBranch),
					git(checkout, "rev-parse", defaultBranch),
					() -> "adopting " + repository + " moved " + defaultBranch + " away from the remote's");
			List<String> published = List.of("git", "rev-parse", "--verify", AdoptionContext.REMOTE + "/" + BRANCH);
			assertFalse(RUNNER.run(checkout, published).succeeded(),
					() -> "adopting " + repository + " published " + BRANCH + " to the remote");
		}
	}

	/**
	 * Re-adoption is the case an operator meets after a run that failed part-way, and on
	 * a foreign build file it is where an installer that cannot recognise its own work
	 * shows: a second run appends the guard again, and the repository is offered a pull
	 * request carrying it twice. The whole batch is run again rather than the installer
	 * alone, so {@link CloneStep}'s reuse of an existing checkout is exercised on these
	 * repositories too.
	 */
	@Test
	void adoptingEveryRepositoryASecondTimeChangesNothing() {
		Map<RealRepository, String> before = heads();

		List<AdoptionRun> second = adoptAll();

		assertTrue(AdoptionRun.allSucceeded(second),
				() -> "the second adoption failed: " + second.stream().map(AdoptionRun::failure).toList());
		assertEquals(before, heads(), "the second adoption committed something");
	}

	/**
	 * The one document in this class that the pipeline reshapes rather than adds beside:
	 * a {@code CLAUDE.md} sixty-odd lines long, written for another purpose entirely,
	 * with a title of its own and none of the sections the {@code claudeMdFormat} rule
	 * asks for. {@link io.github.adamw7.tools.adopt.step.ClaudeMdConformanceStep} runs
	 * the conformer over whatever {@code claude init} produced, and what that is on
	 * somebody else's repository is not something a fixture can be written to imitate.
	 *
	 * <p>The rule is asked, rather than a copy of what it is believed to want, exactly as
	 * {@code ClaudeMdConformerContractTest} asks it — and the foreign document is first
	 * asserted to fail it, so a conformer that quietly stopped reshaping could not pass
	 * this test by doing nothing.
	 */
	@Test
	void aClaudeMdSomebodyElseWroteIsConformedIntoOneTheRuleAccepts() {
		Path claudeMd = checkoutOf(WITH_CLAUDE_MD).resolve(CLAUDE_MD);
		assertTrue(Files.isRegularFile(claudeMd),
				() -> WITH_CLAUDE_MD + " no longer ships a " + CLAUDE_MD + ", so this test needs one that does");
		String foreign = readString(claudeMd);

		assertRuleRejects(foreign);
		assertRuleAccepts(new ClaudeMdConformer().conform(foreign));
	}

	/**
	 * The pipeline stops at the guard commit: nothing here runs {@code claude},
	 * {@code gh}, or the adopted project's own build tool, and nothing writes to GitHub.
	 * The toolchain step is given {@code git} alone for that reason — the tools it
	 * normally requires belong to the steps that are not here.
	 */
	private static GitHubRepoAdopter localAdopter() {
		List<AdoptionStep> steps = List.of(
				new ToolchainStep(List.of("git")),
				new CloneStep(),
				new BranchStep(),
				new EnforcerStep(BUILD_SYSTEMS),
				new CommitStep(GitHubRepoAdopter.GUARD_COMMIT_MESSAGE, "guard"));
		return new GitHubRepoAdopter(RUNNER, steps);
	}

	/**
	 * The run goes through the command line an operator would type, so the five
	 * repositories are one batch into one workspace rather than five runs assembled from
	 * {@link BatchAdoption} inwards.
	 */
	private static List<AdoptionRun> adoptAll() {
		CliArguments cli = CliArguments.parse(commandLine());
		return Main.runAndReport(cli, Main.checkouts(cli), localAdopter());
	}

	private static String[] commandLine() {
		return Stream.concat(
				REPOSITORIES.stream().flatMap(repository -> Stream.of("--repo", repository.url())),
				Stream.of("--workspace", workspace.toString(), "--branch", BRANCH))
				.toArray(String[]::new);
	}

	/**
	 * The listed repository of that name. The test about one repository says which by
	 * name rather than by position, so reordering the list cannot quietly point it at a
	 * different one.
	 */
	private static RealRepository named(String name) {
		return REPOSITORIES.stream()
				.filter(repository -> repository.name().equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No repository called " + name + " is listed"));
	}

	private static Path checkoutOf(RealRepository repository) {
		return workspace.resolve(repository.name());
	}

	/**
	 * The branch the checkout was cloned on, read from the remote's own {@code HEAD}
	 * rather than assumed to be {@code main}: these repositories are somebody else's and
	 * two of them could rename it tomorrow. The remote-tracking name it comes back as is
	 * reduced to the branch, so both refs can be asked for.
	 */
	private static String defaultBranchOf(Path checkout) {
		String tracking = git(checkout, "rev-parse", "--abbrev-ref", AdoptionContext.REMOTE + "/HEAD");
		return tracking.substring(tracking.indexOf('/') + 1);
	}

	/** Each repository's branch tip, so a second adoption can be shown to have added nothing. */
	private static Map<RealRepository, String> heads() {
		Map<RealRepository, String> heads = new LinkedHashMap<>();
		REPOSITORIES.forEach(repository -> heads.put(repository, git(checkoutOf(repository), "rev-parse", "HEAD")));
		return heads;
	}

	/** The paths the guard commit carries, sorted so the expectation reads as a set. */
	private static List<String> committedPaths(RealRepository repository) {
		return git(checkoutOf(repository), "show", "--name-only", "--pretty=format:", "HEAD").lines()
				.filter(line -> !line.isBlank())
				.sorted()
				.toList();
	}

	private void assertRuleAccepts(String content) {
		assertDoesNotThrow(ruleFor(content)::execute,
				"the conformed document must satisfy claudeMdFormat:\n" + content);
	}

	private void assertRuleRejects(String content) {
		assertThrows(EnforcerRuleException.class, ruleFor(content)::execute,
				"the foreign document must start out failing claudeMdFormat, or it proves nothing:\n" + content);
	}

	private ClaudeMdFormatRule ruleFor(String content) {
		return ClaudeMdFormatRule.validating(writeString(conformed.resolve(CLAUDE_MD), content).toFile());
	}

	private static String git(Path directory, String... arguments) {
		List<String> command = Stream.concat(Stream.of("git"), Arrays.stream(arguments)).toList();
		CommandResult result = RUNNER.run(directory, command);
		assertTrue(result.succeeded(), () -> result.describe() + " failed: " + result.output());
		return result.output().strip();
	}
}

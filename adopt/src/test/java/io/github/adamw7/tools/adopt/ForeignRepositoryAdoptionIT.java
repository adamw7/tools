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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
import io.github.adamw7.tools.adopt.command.RetryingCommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionAssets;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.AssetInstaller;
import io.github.adamw7.tools.adopt.step.AssetsStep;
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
 * Adopts seven <em>real</em> repositories, cloned from GitHub over the network, none
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
 * <p>The seven are chosen for the shapes they put in front of the steps that read the
 * checkout: a real multi-module Maven build, a real Gradle build on the Kotlin DSL and
 * another on the Groovy one, a real {@code CLAUDE.md}, a real {@code .claude} directory,
 * a project whose own files already sit where two of the starter assets go, a default
 * branch called neither {@code main} nor {@code master}, and a very large flat tree with
 * no build file at all. Between them they exercise all three {@link BuildSystem}s — and
 * both Gradle DSLs — on build files this repository did not write.
 *
 * <p>What is asserted is that the adoption's work on each of them is <em>its own and
 * nothing else</em>: the guard that lands is the one the checkout's build files ask
 * for, the commits carry only paths {@link AdoptionAssets#WRITTEN_PATHS} names,
 * nothing the project already declared is removed to make room for it, the starter
 * assets installed are the ones the repository was missing and the files it already
 * keeps at their paths are left exactly as they were cloned, the default branch — read
 * from the remote rather than assumed — is where the clone left it, the repository on
 * GitHub is asked directly and has no branch of the adoption's, and adopting the same
 * repository a second time changes nothing.
 *
 * <p>The starter assets are installed here — {@code --include-assets} adds them to a
 * real run — because the promise {@link AssetInstaller} makes is about a file the
 * project already keeps at one of their paths, and only a real repository brings one.
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

	/** A file a repository already keeps at one of {@link #ASSET_PATHS}, which is not the adoption's. */
	private record OwnAsset(RealRepository repository, String path) {

		@Override
		public String toString() {
			return path + " in " + repository;
		}
	}

	private static final String WORKFLOW_GUARD = ".github/workflows/claude-md-guard.yml";
	private static final String GUARD_SCRIPT = ".github/claude-md-guard.sh";

	/** What the {@code github-actions} fallback writes, in the order git reports them. */
	private static final List<String> WORKFLOW_GUARD_PATHS = List.of(GUARD_SCRIPT, WORKFLOW_GUARD);

	private static final String GITHUB_ACTIONS = "github-actions";
	private static final String GRADLE = "gradle";

	private static final String GROOVY_BUILD_FILE = "build.gradle";
	private static final String KOTLIN_BUILD_FILE = "build.gradle.kts";

	/**
	 * How each Gradle DSL spells the registration of the guard task. The two build
	 * scripts here are real ones in different languages, and a block in the other DSL
	 * would still register a task by the name {@link BuildSystem#isGuardInstalled} looks
	 * for while failing to compile in the project it was appended to.
	 */
	private static final Map<String, String> GRADLE_REGISTRATIONS = Map.of(
			GROOVY_BUILD_FILE, "tasks.register('enforceClaudeMd')",
			KOTLIN_BUILD_FILE, "tasks.register(\"enforceClaudeMd\")");

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
					GITHUB_ACTIONS, WORKFLOW_GUARD_PATHS),
			new RealRepository("https://github.com/JakeWharton/timber.git",
					"a real Gradle build on the Groovy DSL, developed on a default branch called neither"
							+ " main nor master",
					GRADLE, List.of(GROOVY_BUILD_FILE)),
			new RealRepository("https://github.com/modelcontextprotocol/servers.git",
					"a project already keeping files of its own where two of the starter assets go",
					GITHUB_ACTIONS, WORKFLOW_GUARD_PATHS));

	/** The repository whose {@code CLAUDE.md} somebody else wrote. */
	private static final RealRepository WITH_CLAUDE_MD = named("anthropic-quickstarts");

	private static final String CLAUDE_WORKFLOW = ".github/workflows/claude.yml";
	private static final String MCP_CONFIG = ".mcp.json";

	/**
	 * The files these repositories were cloned already carrying where a starter asset
	 * goes: a {@code claude.yml} workflow doing a different job from the adoption's
	 * starter one, and an {@code .mcp.json} declaring a server of the project's own. Two
	 * of them, and at different paths, because one asset left alone is as easily an
	 * installer that skips that one path as one that honours what it finds.
	 */
	private static final List<OwnAsset> OWN_ASSETS = List.of(
			new OwnAsset(named("claude-code"), CLAUDE_WORKFLOW),
			new OwnAsset(named("servers"), MCP_CONFIG));

	/** The repository developed on a branch neither {@link #CONVENTIONAL_BRANCHES} names. */
	private static final RealRepository WITH_OWN_DEFAULT_BRANCH = named("timber");

	/** The two names a pipeline that guessed the default branch instead of reading it would guess. */
	private static final List<String> CONVENTIONAL_BRANCHES = List.of("main", "master");

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
	private static final List<String> PIPELINE = List.of(
			"toolchain", "clone", "branch", "enforcer", "commit:guard", "assets", "commit:assets");

	/** The checkout-relative paths {@link AssetsStep} installs, sorted as git reports them. */
	private static final List<String> ASSET_PATHS = AdoptionAssets.DEFAULTS.stream()
			.map(AssetInstaller::relativePath)
			.sorted()
			.toList();

	/**
	 * Generous next to the second or two a clone of these repositories costs, and short
	 * enough that a stalled network fails the class in minutes. The pipeline's own
	 * default is sized for a {@code claude init}, which is not among the steps here.
	 *
	 * <p>Wrapped as a real run wraps it, so a GitHub that refused one of the seven
	 * clones does not report this class's subject — the guard the adoption writes into
	 * somebody else's build file — as broken.
	 */
	private static final CommandRunner RUNNER = new RetryingCommandRunner(
			new ProcessCommandRunner(Duration.ofMinutes(5)), AdoptionOptions.DEFAULT_RETRIES);

	/**
	 * Class-scoped, because the seven clones are what this class costs and every test
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
			assertEquals(repository.guardPaths(), pathsIn(repository, GitHubRepoAdopter.GUARD_COMMIT_MESSAGE),
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
	 *
	 * <p>Both of the run's commits are read, rather than only the branch tip, since each
	 * stages the whole checkout: a stray file written by either would be committed by
	 * whichever ran next and would go unnoticed if only one of them were asked.
	 */
	@Test
	void theAdoptionCommitsOnlyTheFilesItOwnsAndLeavesNothingBehind() {
		for (RealRepository repository : REPOSITORIES) {
			Path checkout = checkoutOf(repository);
			assertEquals(GitHubRepoAdopter.ASSETS_COMMIT_MESSAGE, git(checkout, "log", "-1", "--format=%s"),
					() -> "the tip of " + BRANCH + " in " + repository + " is not the adoption's last commit");
			for (String path : adoptedPaths(repository)) {
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
			List<String> removals = git(checkoutOf(repository), "show", "--unified=0", "--pretty=format:",
					commitOf(repository, GitHubRepoAdopter.GUARD_COMMIT_MESSAGE))
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
	 * pushes. The first is asserted against the refs {@code git clone} produced, so a
	 * step that committed on the wrong branch is caught here rather than on somebody's
	 * repository.
	 *
	 * <p>The second is asked of GitHub itself. A pipeline that grew a push would leave a
	 * remote-tracking ref behind, which is checked too because it costs nothing — but a
	 * ref in this checkout is evidence about this checkout, and the claim is about seven
	 * repositories that belong to other people. Only {@code ls-remote} answers that, and
	 * it answers it for a push this class knew nothing about as readily as for one of
	 * its own.
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
			List<String> tracked = List.of("git", "rev-parse", "--verify", AdoptionContext.REMOTE + "/" + BRANCH);
			assertFalse(RUNNER.run(checkout, tracked).succeeded(),
					() -> "adopting " + repository + " left a remote-tracking ref for " + BRANCH);
			assertEquals("", git(checkout, "ls-remote", "--heads", AdoptionContext.REMOTE, "refs/heads/" + BRANCH),
					() -> "adopting " + repository + " published " + BRANCH + " to the repository itself");
		}
	}

	/**
	 * The starter assets arrive in a repository that already has files of its own, and
	 * {@link AssetsStep} installs exactly the ones it is missing. Asserting the commit's
	 * paths against the difference — rather than against a list written out here — is
	 * what makes the claim hold for whatever these seven repositories ship next: an
	 * installer that started overwriting would commit a path the project already
	 * tracked, and one that stopped installing would leave a missing path out.
	 */
	@Test
	void theStarterAssetsInstalledAreTheOnesTheRepositoryDidNotAlreadyHave() {
		for (RealRepository repository : REPOSITORIES) {
			List<String> alreadyThere = assetsAlreadyIn(repository);
			List<String> missing = ASSET_PATHS.stream().filter(path -> !alreadyThere.contains(path)).toList();
			assertEquals(missing, pathsIn(repository, GitHubRepoAdopter.ASSETS_COMMIT_MESSAGE),
					() -> "adopting " + repository + " did not install exactly the assets it was missing;"
							+ " it already had " + alreadyThere);
		}
	}

	/**
	 * The two repositories here that already ship a file at an asset's path — a
	 * {@code claude.yml} workflow of its own, doing a different job from the adoption's
	 * starter one, and an {@code .mcp.json} declaring a server of the project's own.
	 * {@link AssetInstaller} promises the project's version always wins, and every
	 * fixture that promise is checked against holds a file this repository wrote to be
	 * overwritten. Comparing against the blob on the cloned default branch is what makes
	 * it somebody else's file: nothing here says what either should contain.
	 */
	@Test
	void theProjectsOwnFilesAtAnAssetsPathAreLeftExactlyAsTheyWereCloned() {
		for (OwnAsset own : OWN_ASSETS) {
			Path checkout = checkoutOf(own.repository());
			assertTrue(Files.isRegularFile(checkout.resolve(own.path())),
					() -> own.repository() + " no longer ships " + own.path() + ", so this test needs a repository"
							+ " that carries a file at one of " + ASSET_PATHS);
			assertTrue(assetsAlreadyIn(own.repository()).contains(own.path()),
					() -> own + " must be the project's own, or overwriting it would prove nothing");
			assertEquals("", git(checkout, "diff", defaultBranchOf(checkout), "HEAD", "--", own.path()),
					() -> "adopting " + own.repository() + " changed the project's own " + own.path());
		}
	}

	/**
	 * Which Gradle DSL the guard is written in is read from the build script's own
	 * extension, and only a repository of each kind puts that decision in front of a
	 * real script. Both blocks register a task by the same name, so an installer that
	 * appended the Kotlin one to a Groovy script would pass every other assertion here
	 * — the guard commit carries the right path, removes nothing, and declares the task
	 * — and leave the adopted project a build script that no longer compiles.
	 */
	@Test
	void eachGradleScriptIsGivenTheGuardWrittenInItsOwnDsl() {
		for (RealRepository repository : gradleRepositories()) {
			String buildFile = repository.guardPaths().getFirst();
			String added = addedBy(repository, GitHubRepoAdopter.GUARD_COMMIT_MESSAGE);
			assertTrue(added.contains(GRADLE_REGISTRATIONS.get(buildFile)),
					() -> "the guard appended to " + buildFile + " in " + repository + " is not the one the "
							+ buildFile + " DSL registers a task with: " + added);
			assertFalse(added.contains(otherDslRegistration(buildFile)),
					() -> "the guard appended to " + buildFile + " in " + repository + " is written in the other"
							+ " Gradle DSL: " + added);
		}
	}

	/**
	 * The default branch is read from the remote rather than assumed, and one repository
	 * here is what makes the reading matter: {@code timber} develops on {@code trunk}. A
	 * pipeline that took {@code main} for the default would clone this repository, cut
	 * its branch from the wrong ref or from none, and pass every other assertion in this
	 * class on the six that are conventionally named.
	 */
	@Test
	void aRepositoryDevelopedOnItsOwnDefaultBranchIsAdoptedFromThatBranch() {
		Path checkout = checkoutOf(WITH_OWN_DEFAULT_BRANCH);
		String defaultBranch = defaultBranchOf(checkout);

		assertFalse(CONVENTIONAL_BRANCHES.contains(defaultBranch),
				() -> WITH_OWN_DEFAULT_BRANCH + " now develops on " + defaultBranch + ", so this test needs a"
						+ " repository whose default branch is none of " + CONVENTIONAL_BRANCHES);
		assertEquals(git(checkout, "rev-parse", defaultBranch),
				git(checkout, "merge-base", defaultBranch, BRANCH),
				() -> "adopting " + WITH_OWN_DEFAULT_BRANCH + " cut " + BRANCH + " from something other than "
						+ defaultBranch);
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
				new CommitStep(GitHubRepoAdopter.GUARD_COMMIT_MESSAGE, "guard"),
				new AssetsStep(),
				new CommitStep(GitHubRepoAdopter.ASSETS_COMMIT_MESSAGE, "assets"));
		return new GitHubRepoAdopter(RUNNER, steps);
	}

	/**
	 * The run goes through the command line an operator would type, so the seven
	 * repositories are one batch into one workspace rather than seven runs assembled from
	 * {@link BatchAdoption} inwards.
	 */
	private static List<AdoptionRun> adoptAll() {
		CliArguments cli = CliArguments.parse(commandLine());
		return Main.runAndReport(cli, Main.checkouts(cli), localAdopter());
	}

	/**
	 * The checkouts are kept because every assertion below reads one: what the
	 * adoption committed, what it left alone, what the guard it wired in looks like.
	 * An ordinary run removes the checkout of an adoption that landed, its product
	 * being the pushed branch and the pull request.
	 */
	private static String[] commandLine() {
		return Stream.concat(
				REPOSITORIES.stream().flatMap(repository -> Stream.of("--repo", repository.url())),
				Stream.of("--workspace", workspace.toString(), "--branch", BRANCH, "--keep-workspace"))
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

	/**
	 * The commit the adoption made under that message, named rather than counted back
	 * from the branch tip: the run leaves two commits and reordering the pipeline would
	 * otherwise quietly point a test at the other one. The search is bounded to the
	 * commits the branch added, so a message that happens to appear in the project's own
	 * history cannot answer for the adoption's.
	 */
	private static String commitOf(RealRepository repository, String message) {
		Path checkout = checkoutOf(repository);
		String commit = git(checkout, "log", "--format=%H", "--fixed-strings", "--grep=" + message,
				defaultBranchOf(checkout) + ".." + BRANCH);
		assertFalse(commit.isBlank(), () -> "adopting " + repository + " left no commit saying " + message);
		return commit;
	}

	/** The listed repositories the adoption wires a Gradle guard into, one per DSL. */
	private static List<RealRepository> gradleRepositories() {
		List<RealRepository> gradle = REPOSITORIES.stream()
				.filter(repository -> GRADLE.equals(repository.buildSystem()))
				.toList();
		assertEquals(GRADLE_REGISTRATIONS.keySet(),
				gradle.stream().map(repository -> repository.guardPaths().getFirst())
						.collect(Collectors.toSet()),
				() -> "this test needs one real repository per Gradle DSL, and has " + gradle);
		return gradle;
	}

	/** The registration belonging to the DSL the build file is not written in. */
	private static String otherDslRegistration(String buildFile) {
		return GRADLE_REGISTRATIONS.get(GROOVY_BUILD_FILE.equals(buildFile) ? KOTLIN_BUILD_FILE : GROOVY_BUILD_FILE);
	}

	/** The lines that commit added, which is where a guard block written for it appears. */
	private static String addedBy(RealRepository repository, String message) {
		return git(checkoutOf(repository), "show", "--unified=0", "--pretty=format:",
				commitOf(repository, message)).lines()
				.filter(line -> line.startsWith("+") && !line.startsWith("+++ "))
				.collect(Collectors.joining("\n"));
	}

	/** The paths that commit carries, sorted so the expectation reads as a set. */
	private static List<String> pathsIn(RealRepository repository, String message) {
		return git(checkoutOf(repository), "show", "--name-only", "--pretty=format:",
				commitOf(repository, message)).lines()
				.filter(line -> !line.isBlank())
				.sorted()
				.toList();
	}

	/**
	 * Every path the adoption's commits carry, read commit by commit rather than as the
	 * range's net diff: a file one commit added and the next removed nets out to nothing,
	 * and it is exactly the file the adoption could not account for.
	 */
	private static List<String> adoptedPaths(RealRepository repository) {
		Path checkout = checkoutOf(repository);
		return git(checkout, "log", "--format=", "--name-only", defaultBranchOf(checkout) + ".." + BRANCH).lines()
				.filter(line -> !line.isBlank())
				.distinct()
				.sorted()
				.toList();
	}

	/**
	 * The asset paths the repository was cloned already carrying, read from the default
	 * branch rather than from the working tree, which by now holds the adoption's files
	 * beside the project's own.
	 */
	private static List<String> assetsAlreadyIn(RealRepository repository) {
		Path checkout = checkoutOf(repository);
		String[] arguments = Stream.concat(
				Stream.of("ls-tree", "-r", "--name-only", defaultBranchOf(checkout), "--"),
				ASSET_PATHS.stream()).toArray(String[]::new);
		return git(checkout, arguments).lines()
				.filter(ASSET_PATHS::contains)
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

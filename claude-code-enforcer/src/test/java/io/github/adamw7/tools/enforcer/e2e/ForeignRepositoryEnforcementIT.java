package io.github.adamw7.tools.enforcer.e2e;

import static io.github.adamw7.tools.test.TestFiles.readString;
import static io.github.adamw7.tools.test.TestFiles.writeString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Points every shipped rule at five <em>real</em> repositories, cloned from GitHub
 * over the network, none of which has ever heard of this enforcer.
 *
 * <p>{@link EnforcerRuleBuildIT} proves the rules bind and fire, and
 * {@link RepositoryEnforcementIT} proves the configuration this repository ships
 * guards this repository. Both work on files written to be read by these rules:
 * the fixture is laid out correctly on purpose so that breaking one thing proves
 * one rule, and this checkout satisfies the contract because it was written to.
 * Neither can answer the question an adopter asks first — what these rules do when
 * they meet a project nobody prepared for them.
 *
 * <p>What is asserted is therefore not that a foreign repository passes: none of
 * these five does, and none should. It is that every rule <em>reaches a verdict</em>
 * on every one of them — a pass, or a failure with a message that names what is
 * wrong — rather than failing the build on the rules' own account with an
 * unbindable parameter, an internal error, or an exception escaping from a file it
 * did not expect. A rule that reads a project correctly and a rule that breaks on it
 * both leave a red build; only the log tells them apart, which is what
 * {@link EnforcerVerdicts} reads.
 *
 * <p>The five are chosen for the shapes they put in front of the rules — an almost
 * empty repository, a small one, a very large flat one, a real {@code .claude}
 * directory somebody else wrote, and a real {@code CLAUDE.md} somebody else wrote —
 * and every clone is shallow, so the whole class costs about as much as one of the
 * builds it runs. Nothing is written to any of them: the enforcing build runs in a
 * directory of its own and only the rule parameters point at a checkout.
 */
class ForeignRepositoryEnforcementIT {

	/**
	 * A repository worth pointing the rules at, and what it is that makes it worth
	 * pointing them at — the second is quoted into every assertion message, so a
	 * failure says which shape of project defeated a rule rather than only which URL.
	 *
	 * @param url   the clone URL, over HTTPS so no key is needed
	 * @param shape what this repository holds that the fixture cannot imitate
	 */
	private record RealRepository(String url, String shape) {

		@Override
		public String toString() {
			return GitClone.nameOf(url) + " (" + shape + ")";
		}
	}

	private static final List<RealRepository> REPOSITORIES = List.of(
			new RealRepository("https://github.com/octocat/Hello-World.git",
					"one README and nothing else, so every rule is pointed at something absent"),
			new RealRepository("https://github.com/octocat/Spoon-Knife.git",
					"a small web page with no agent configuration of any kind"),
			new RealRepository("https://github.com/github/gitignore.git",
					"thousands of small files in one flat tree"),
			new RealRepository("https://github.com/anthropics/claude-code.git",
					"a real .claude/commands directory, and a .claude-plugin holding a "
							+ "marketplace.json rather than the plugin.json a rule looks for"),
			new RealRepository("https://github.com/anthropics/anthropic-quickstarts.git",
					"a real CLAUDE.md, written by someone who never heard of these rules"));

	/**
	 * The property the harness pom addresses the checkout through. One pom serves all
	 * five repositories because only this changes between them.
	 */
	private static final String REPOSITORY_PROPERTY = "claude.repository";

	/** The property the baseline harness is handed its baseline file through. */
	private static final String BASELINE_PROPERTY = "claude.baseline";

	private static final String HARNESS_ARTIFACT_ID = "foreign-repository-harness";
	private static final String BASELINE_HARNESS_ARTIFACT_ID = "foreign-repository-baseline-harness";

	/**
	 * The rules whose file is optional, which must pass when a project does not ship
	 * it. Each is paired with the path {@link RuleConfiguration#COMPLETE} points it at,
	 * relative to the repository, so the expectation is checked only where the file
	 * really is absent rather than assumed of every repository alike.
	 */
	private static final Map<String, String> OPTIONAL_RULE_TARGETS = Map.of(
			"pluginFormat", ".claude-plugin/plugin.json",
			"mcpServersValid", ".mcp.json",
			"mcpConfigFormat", ".mcp.json",
			"okfBundleFormat", "okf");

	/**
	 * Ways a build can go red that are the rules' fault rather than the project's. The
	 * first is Plexus refusing to bind a configuration element — the failure
	 * {@link EnforcerRuleBuildIT} provokes on purpose with a misspelled parameter — and
	 * the rest are a rule breaking on input it did not expect instead of reporting it.
	 */
	private static final List<String> RULE_SIDE_FAILURES = List.of(
			"Cannot find '",
			"NullPointerException",
			"ClassCastException",
			"IndexOutOfBoundsException",
			"StackOverflowError",
			"OutOfMemoryError",
			"Internal error in the plugin manager");

	private static final String CLAUDE_MD_FORMAT = "claudeMdFormat";
	private static final String CLAUDE_MD = "CLAUDE.md";

	private static final String COMMAND_FORMAT = "commandFormat";
	private static final String COMMANDS_DIRECTORY = ".claude/commands";
	private static final String ABSENT_COMMANDS = "Commands directory does not exist";

	/**
	 * One rule, pointed at a checkout and recording what it finds there. It is one
	 * rule rather than the complete block because a baseline suppresses collected
	 * violations and nothing else: a rule pointed at a file a repository has not got
	 * fails on the build-setup check before it collects anything, and no baseline can
	 * — or should — accept that. {@code commandFormat} is the rule that has something
	 * to collect on a foreign checkout and names the file it collected it from, which
	 * is what makes the recorded signatures worth carrying between clones at all.
	 *
	 * <p>{@code baseDir} is the checkout rather than {@code ${project.basedir}}: the
	 * project this build builds is the harness beside the repository, so the directory
	 * the {@code ${basedir}} token has to stand for is the one the rule was pointed at,
	 * not the one Maven was run in.
	 */
	private static final String BASELINED_COMMAND_RULE = """
								<commandFormat>
									<commandsDir>${claude.repository}/.claude/commands</commandsDir>
									<allowedFrontMatterKeys>
										<allowedFrontMatterKey>description</allowedFrontMatterKey>
										<allowedFrontMatterKey>model</allowedFrontMatterKey>
									</allowedFrontMatterKeys>
									<baselineFile>${claude.baseline}</baselineFile>
									<baseDir>${claude.repository}</baseDir>
								</commandFormat>
			""";

	private static final String WRITE_BASELINE = "-Dclaude.enforcer.writeBaseline=true";
	private static final String SUPPRESSED_BY_BASELINE = "violation(s) suppressed by the baseline";

	/** What a recorded signature carries in place of the checkout it was recorded against. */
	private static final String BASE_DIR_TOKEN = "${basedir}";

	private static final String BASELINE_COMMENT = "#";

	/** A command whose name breaks the convention, which no foreign backlog can already hold. */
	private static final String NEW_COMMAND = "Not-A-Command";
	private static final String NEW_COMMAND_BODY = """
			Added by a test, to be the one violation the baseline has never seen.
			""";

	/** The repository whose {@code .claude} directory somebody else wrote. */
	private static final RealRepository WITH_AGENT_CONFIGURATION = named("claude-code");

	/**
	 * A repository with no agent configuration at all, which is the state a project is
	 * in on the day it adopts one.
	 */
	private static final RealRepository WITHOUT_AGENT_CONFIGURATION = named("Spoon-Knife");

	private static BuildEnvironment environment;
	private static MavenBuild maven;
	private static String enforcerPluginVersion;
	private static List<Enforcement> enforcements;

	/**
	 * Class-scoped, because the clones and the five builds over them are what this
	 * class costs and every test below reads the same results. The two tests that
	 * change a checkout take a fresh clone of their own, into {@link #adoption} and
	 * {@link #replay}.
	 */
	@TempDir
	static Path workspace;

	@TempDir
	Path adoption;

	@TempDir
	Path replay;

	/** One repository, its checkout, and what the enforcing build over it said. */
	private record Enforcement(RealRepository repository, Path checkout, BuildOutcome outcome,
			EnforcerVerdicts verdicts) {
	}

	@BeforeAll
	static void enforceEveryRepository() {
		environment = new BuildEnvironment();
		RepositoryPom repositoryPom = new RepositoryPom(environment.repositoryRoot());
		enforcerPluginVersion = repositoryPom.pluginVersion("maven-enforcer-plugin");
		maven = new MavenBuild(environment);
		maven.publishRuleUnderTest(repositoryPom.pluginVersion("maven-install-plugin"));
		enforcements = enforce(harness(), checkouts());
	}

	/**
	 * The claim this class exists for. Every rule the module ships is asked about every
	 * one of the five, and every one of them has to answer: a rule that reached no
	 * verdict either never ran — a misspelled {@code @Named} name, an execution that
	 * did not fire — or stopped the build before the ones after it could run, and in
	 * both cases a project that trusted it is guarded by nothing.
	 */
	@Test
	void everyShippedRuleReachesAVerdictOnEveryRealRepository() {
		for (Enforcement enforcement : enforcements) {
			for (String rule : ShippedRules.byName().keySet()) {
				assertTrue(enforcement.verdicts().reached(rule),
						() -> rule + " reached no verdict on " + enforcement.repository() + ": "
								+ enforcement.outcome().describe());
			}
		}
	}

	/**
	 * A red build is the expected outcome here — none of these repositories ships this
	 * contract — but it has to be red for the project's reasons, not the rules'. An
	 * unbindable parameter or an exception thrown out of a rule fails a build with a
	 * message about the enforcer, which tells the adopter nothing about their project
	 * and is indistinguishable, from the exit code alone, from a rule working
	 * perfectly.
	 */
	@Test
	void noRuleFailsARealRepositoryOnItsOwnAccount() {
		for (Enforcement enforcement : enforcements) {
			for (String failure : RULE_SIDE_FAILURES) {
				assertFalse(enforcement.outcome().mentions(failure),
						() -> "enforcing " + enforcement.repository() + " broke a rule rather than the project ("
								+ failure + "): " + enforcement.outcome().describe());
			}
		}
	}

	/**
	 * Every failure has to be actionable, which starts with saying something. A rule
	 * that failed with an empty message leaves an adopter a red build and no sentence
	 * to act on, and nothing in the exit code would show it.
	 */
	@Test
	void everyRuleThatFailsARealRepositorySaysWhy() {
		for (Enforcement enforcement : enforcements) {
			for (String rule : enforcement.verdicts().failed()) {
				assertFalse(enforcement.verdicts().messageOf(rule).isBlank(),
						() -> rule + " failed " + enforcement.repository() + " without saying why: "
								+ enforcement.outcome().describe());
			}
		}
	}

	/**
	 * "Says why" is only worth something if the message is about the reader's project,
	 * so one rule is followed all the way through: none of these five ships a document
	 * titled {@code # CLAUDE.md} carrying the six required sections, so
	 * {@code claudeMdFormat} fails every one of them, and whether the document is
	 * absent or merely someone else's, what it failed with has to name it.
	 *
	 * <p>This is also what proves {@link EnforcerVerdicts} recovers the messages rather
	 * than only the verdicts: a parser that lost them would leave every message empty,
	 * and an empty message names nothing.
	 */
	@Test
	void aRuleThatFailsARealRepositoryNamesTheDocumentItIsAbout() {
		for (Enforcement enforcement : enforcements) {
			assertTrue(enforcement.verdicts().messageOf(CLAUDE_MD_FORMAT).contains(CLAUDE_MD),
					() -> CLAUDE_MD_FORMAT + " did not fail " + enforcement.repository() + " with a message naming "
							+ CLAUDE_MD + ": " + enforcement.outcome().describe());
		}
	}

	/**
	 * Four rules target files a project need not have, and the documented behaviour is
	 * that an absent one is a pass rather than a failure. That is exactly the promise a
	 * repository adopting the rules one at a time depends on, and these five are where
	 * it is really tested: the fixture ships all four files, so it can only show what
	 * happens when they are there.
	 *
	 * <p>Each is checked only against the repositories that genuinely lack its file, so
	 * a repository that grows an {@code .mcp.json} tomorrow moves out of this test's
	 * scope instead of failing it for the wrong reason.
	 */
	@Test
	void theOptionalRulesPassOnARepositoryThatShipsNoneOfTheirFiles() {
		for (Enforcement enforcement : enforcements) {
			OPTIONAL_RULE_TARGETS.forEach((rule, target) -> assertOptionalRulePasses(enforcement, rule, target));
		}
	}

	/**
	 * The one repository here that ships an agent configuration somebody else wrote. Its
	 * command documents carry front matter this repository never invented — an
	 * {@code allowed-tools} key the fixture's own commands do not use — and reading
	 * them is the only thing in this class that puts a rule in front of real, foreign
	 * front matter.
	 *
	 * <p>What is pinned is that the rule read those documents: a verdict reached, and
	 * not the one it gives a directory that is not there. Whether it then approves of
	 * them is the repository's business and may change with any commit; that it looked
	 * is the rule's.
	 */
	@Test
	void theRulesReadAForeignAgentConfigurationRatherThanReportingItAbsent() {
		Enforcement enforcement = enforcementOf(WITH_AGENT_CONFIGURATION);
		List<Path> commands = markdownIn(enforcement.checkout().resolve(COMMANDS_DIRECTORY));

		assertFalse(commands.isEmpty(),
				() -> WITH_AGENT_CONFIGURATION + " no longer ships command definitions under " + COMMANDS_DIRECTORY
						+ ", so this test needs a repository that does");
		assertTrue(enforcement.verdicts().reached(COMMAND_FORMAT),
				() -> COMMAND_FORMAT + " reached no verdict: " + enforcement.outcome().describe());
		assertFalse(enforcement.verdicts().messageOf(COMMAND_FORMAT).contains(ABSENT_COMMANDS),
				() -> COMMAND_FORMAT + " reported " + commands.size() + " real command definition(s) absent: "
						+ enforcement.outcome().describe());
	}

	/**
	 * The other half of the claim: the rules are not only survivable but satisfiable
	 * outside the repository that wrote them. A real checkout is adopted the way a
	 * project would be — the contract's documents and {@code .claude} configuration
	 * written into it — and the same complete configuration then passes, with the
	 * repository's own files, its {@code .git} directory and its history still around
	 * them.
	 *
	 * <p>It takes a clone of its own because it is the one test here that writes into a
	 * checkout, and the five the other tests share are read as {@code git} produced
	 * them.
	 */
	@Test
	void aRealRepositorySatisfiesTheContractOnceItIsAdopted() {
		Path checkout = GitClone.into(adoption, WITHOUT_AGENT_CONFIGURATION.url());
		FixtureProject adopted = new FixtureProject(checkout, environment, enforcerPluginVersion)
				.layOutWellFormedProject()
				.enforcing(RuleConfiguration.complete().xml());

		BuildOutcome outcome = maven.run(adopted.root(), "-N", "validate");

		assertTrue(outcome.succeeded(),
				() -> "the contract cannot be satisfied in " + WITHOUT_AGENT_CONFIGURATION + ": "
						+ outcome.describe());
	}

	/**
	 * The step an adopter takes when a rule has more to say about their project than
	 * they can fix today: record the backlog, gate what is added to it. A baseline is
	 * a file the project commits, so it is recorded on one clone and read on every
	 * other — a developer's and CI's — and a signature naming the absolute path it was
	 * recorded under would match nothing anywhere else, leaving a build that was
	 * supposed to be green failing on the whole backlog it had just accepted.
	 *
	 * <p>The fixture cannot show this. Its three baseline builds run in one directory,
	 * so a signature that never left it round-trips whether it was normalised or not.
	 * Here the backlog is a real one nobody wrote for these rules — the commands in
	 * {@code claude-code} declare an {@code allowed-tools} key the configuration does
	 * not allow — and it is recorded against the checkout the other tests share and
	 * replayed against a second clone of the same repository, at a path the recording
	 * never saw.
	 *
	 * <p>All three steps are builds, and only the third may be red: the recording
	 * passes, the replay passes with the backlog suppressed, and a command added to
	 * the second clone alone fails it — reported on its own, with everything the
	 * baseline accepted still unmentioned, which is the difference between a gate and
	 * a rule switched off.
	 */
	@Test
	void aBaselineRecordedOnOneCloneSuppressesTheSameBacklogInAnother() {
		Path harness = baselineHarness();
		Path baseline = workspace.resolve("baseline/command-format.txt");
		Path recordedAgainst = enforcementOf(WITH_AGENT_CONFIGURATION).checkout();
		Path replayedAgainst = GitClone.into(replay, WITH_AGENT_CONFIGURATION.url());

		BuildOutcome recording = enforceWithBaseline(harness, recordedAgainst, baseline, WRITE_BASELINE);
		assertTrue(recording.succeeded(), recording::describe);
		List<String> accepted = acceptedBy(baseline);
		assertFalse(accepted.isEmpty(),
				() -> COMMAND_FORMAT + " found nothing to record in " + WITH_AGENT_CONFIGURATION
						+ ", so this test needs a repository whose commands it has something to say about: "
						+ recording.describe());
		assertFalse(readString(baseline).contains(recordedAgainst.toString()),
				() -> "a baseline naming the clone it was recorded against travels to no other: "
						+ readString(baseline));

		BuildOutcome replayed = enforceWithBaseline(harness, replayedAgainst, baseline);
		assertTrue(replayed.succeeded(),
				() -> "the backlog recorded in " + recordedAgainst + " suppressed nothing in " + replayedAgainst
						+ ": " + replayed.describe());
		assertTrue(replayed.mentions(SUPPRESSED_BY_BASELINE), replayed::describe);

		writeString(replayedAgainst.resolve(COMMANDS_DIRECTORY).resolve(NEW_COMMAND + ".md"), NEW_COMMAND_BODY);
		BuildOutcome regressed = enforceWithBaseline(harness, replayedAgainst, baseline);
		assertFalse(regressed.succeeded(),
				() -> "a violation no baseline accepted must still fail the build: " + regressed.describe());
		assertTrue(regressed.mentions(NEW_COMMAND),
				() -> "the failure must name the command that was added: " + regressed.describe());
		for (String entry : accepted) {
			assertFalse(regressed.mentions(entry.replace(BASE_DIR_TOKEN, replayedAgainst.toString())),
					() -> "a violation the baseline accepted was reported again: " + regressed.describe());
		}
	}

	private void assertOptionalRulePasses(Enforcement enforcement, String rule, String target) {
		Path file = enforcement.checkout().resolve(target);
		if (!Files.exists(file)) {
			assertTrue(enforcement.verdicts().passed(rule),
					() -> rule + " must pass on " + enforcement.repository() + ", which has no " + target + ": "
							+ enforcement.outcome().describe());
		}
	}

	/**
	 * The listed repository of that name. Two tests are about one repository each and
	 * say which by name rather than by position, so reordering the list cannot quietly
	 * point them at a different one.
	 */
	private static RealRepository named(String name) {
		return REPOSITORIES.stream()
				.filter(repository -> GitClone.nameOf(repository.url()).equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No repository called " + name + " is listed"));
	}

	private Enforcement enforcementOf(RealRepository repository) {
		return enforcements.stream()
				.filter(enforcement -> enforcement.repository().equals(repository))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(repository + " was not enforced"));
	}

	/**
	 * The build that enforces a checkout, written once and outside every one of them.
	 * The rules are addressed through a property rather than the project's own
	 * directory, so the same pom serves each repository in turn and none of them is
	 * given a {@code pom.xml} it did not have.
	 */
	private static Path harness() {
		Path directory = workspace.resolve("harness");
		String rules = RuleConfiguration.against("${" + REPOSITORY_PROPERTY + "}").xml();
		writeString(directory.resolve("pom.xml"),
				new EnforcerPom(environment, enforcerPluginVersion).enforcing(HARNESS_ARTIFACT_ID, rules));
		return directory;
	}

	/**
	 * The build that records and replays a baseline, written beside the checkouts for
	 * the same reason the harness is: a repository is never given a {@code pom.xml},
	 * and neither is it given the baseline file, which belongs to the project that
	 * commits one rather than to the one being read.
	 */
	private Path baselineHarness() {
		Path directory = workspace.resolve("baseline-harness");
		writeString(directory.resolve("pom.xml"), new EnforcerPom(environment, enforcerPluginVersion)
				.enforcing(BASELINE_HARNESS_ARTIFACT_ID, BASELINED_COMMAND_RULE));
		return directory;
	}

	private BuildOutcome enforceWithBaseline(Path harness, Path checkout, Path baseline, String... arguments) {
		List<String> command = new ArrayList<>(List.of("-N", "validate",
				"-D" + REPOSITORY_PROPERTY + "=" + checkout,
				"-D" + BASELINE_PROPERTY + "=" + baseline));
		command.addAll(List.of(arguments));
		return maven.run(harness, command.toArray(String[]::new));
	}

	/** The violations a recorded baseline accepts: its lines, less the header comments. */
	private List<String> acceptedBy(Path baseline) {
		return readString(baseline).lines()
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith(BASELINE_COMMENT))
				.toList();
	}

	private static List<Path> checkouts() {
		Path into = workspace.resolve("checkouts");
		return REPOSITORIES.stream().map(repository -> GitClone.into(into, repository.url())).toList();
	}

	private static List<Enforcement> enforce(Path harness, List<Path> checkouts) {
		List<Enforcement> results = new ArrayList<>();
		for (int index = 0; index < REPOSITORIES.size(); index++) {
			results.add(enforce(harness, REPOSITORIES.get(index), checkouts.get(index)));
		}
		return results;
	}

	private static Enforcement enforce(Path harness, RealRepository repository, Path checkout) {
		BuildOutcome outcome = maven.run(harness, "-N", "validate",
				"-D" + REPOSITORY_PROPERTY + "=" + checkout);
		return new Enforcement(repository, checkout, outcome, EnforcerVerdicts.in(outcome));
	}

	/** The {@code *.md} files directly in a directory, and none when there is no directory. */
	private static List<Path> markdownIn(Path directory) {
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (Stream<Path> entries = Files.list(directory)) {
			return entries.filter(path -> path.getFileName().toString().endsWith(".md")).toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not list " + directory, e);
		}
	}
}

package io.github.adamw7.tools.enforcer.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the shipped rules the way a project meets them: as elements of a
 * {@code pom.xml}, evaluated by a real maven-enforcer-plugin, in a real Maven
 * build, against a real directory of files.
 *
 * <p>The unit tests construct each rule, call its setters and invoke
 * {@code execute()}. Everything between the pom and that call is theirs to assume:
 * that Maven can resolve the rule jar, that Sisu finds the class behind a
 * {@code @Named} element, that Plexus binds {@code <maxLineLength>} to the field
 * of that name, and that a violation collected in a list comes back out as a
 * failed build rather than a passed one. Each of those is a way a rule can be
 * correct and still never run — a misspelled parameter fails the build outright, a
 * misspelled <em>rule</em> name never runs at all — and none of them is observable
 * from inside the rule.
 *
 * <p>What is asserted here is therefore the seam, not the rules' logic: that the
 * whole catalogue binds ({@link #theCompleteConfigurationAddressesEveryRuleAndParameterTheModuleShips}
 * pins the fixture to the classes so a new rule cannot quietly skip this), that a
 * well-formed project passes every one of them, and that the shared behaviours the
 * base class offers — collect-then-report, {@code severity=warn}, the HTML report,
 * the baseline, and the build-setup checks that fail whatever the severity —
 * survive the crossing into Maven.
 *
 * <p>Each build takes about a second against the local repository the outer build
 * already populated, and the rule jar it resolves is the one this module has just
 * packaged, published into that repository by {@link #publishTheRuleUnderTest()}.
 */
class EnforcerRuleBuildIT {

	/** Only the sections a well-formed CLAUDE.md needs are gone, so the rule has several things to say at once. */
	private static final String MALFORMED_CLAUDE_MD = """
			# CLAUDE.md

			## Project

			A fixture that has lost most of its structure.
			""";

	/** Adds a violation to {@link #MALFORMED_CLAUDE_MD} without disturbing the ones already there. */
	private static final String WRONGLY_TITLED_CLAUDE_MD = """
			# Claude instructions

			## Project

			A fixture that has lost most of its structure.
			""";

	private static final String MISSING_SECTION = "CLAUDE.md is missing required section heading: ";
	private static final String MISSING_AGENTS_REFERENCE = "CLAUDE.md must reference AGENTS.md";
	private static final String WRONG_TITLE = "CLAUDE.md must start with the '# CLAUDE.md' title heading";

	private static BuildEnvironment environment;
	private static MavenBuild maven;
	private static String enforcerPluginVersion;

	@TempDir
	Path workspace;

	@BeforeAll
	static void publishTheRuleUnderTest() {
		environment = new BuildEnvironment();
		RepositoryPom repositoryPom = new RepositoryPom(environment.repositoryRoot());
		enforcerPluginVersion = repositoryPom.pluginVersion("maven-enforcer-plugin");
		maven = new MavenBuild(environment);
		maven.publishRuleUnderTest(repositoryPom.pluginVersion("maven-install-plugin"));
	}

	/**
	 * The fixture is held against the compiled classes rather than a list someone
	 * remembered to update: a rule added to the module, or a parameter added to a
	 * rule, that no fixture configures is named here instead of quietly going
	 * unexercised by every test below.
	 */
	@Test
	void theCompleteConfigurationAddressesEveryRuleAndParameterTheModuleShips() {
		Map<String, Class<?>> shipped = ShippedRules.byName();
		RuleConfiguration configuration = RuleConfiguration.complete();

		assertEquals(shipped.keySet(), configuration.ruleNames(),
				"every shipped rule must be configured by the end-to-end fixture");
		shipped.forEach((name, rule) -> assertEquals(ShippedRules.parametersOf(rule),
				configuration.parametersOf(name), () -> "the fixture must configure every parameter of " + name));
	}

	/**
	 * The catalogue in one build: twenty-odd rules, each configured with every
	 * parameter it accepts, against a project laid out the way an adopted repository
	 * is. A green build here is what proves each {@code @Named} name resolves to a
	 * rule class and each configuration element to a field — Maven fails outright on
	 * an element it cannot bind — and each rule is checked to have actually run,
	 * since a rule that is never reached also never fails.
	 */
	@Test
	void aWellFormedProjectPassesEveryRuleInARealMavenBuild() {
		FixtureProject project = wellFormedProject().enforcing(RuleConfiguration.complete().xml());

		BuildOutcome outcome = validate(project);

		assertTrue(outcome.succeeded(), outcome::describe);
		for (String name : ShippedRules.byName().keySet()) {
			assertTrue(outcome.mentions("(" + name + ") passed"),
					() -> name + " did not report a pass: " + outcome.describe());
		}
	}

	/**
	 * A rule collects every problem before reporting, so one build tells a
	 * contributor everything that is wrong with the document. That is worth little if
	 * the grouped message is truncated on its way through Maven, so all six
	 * violations are looked for in the failed build's own output.
	 */
	@Test
	void aMalformedDocumentFailsTheBuildListingEveryViolationAtOnce() {
		FixtureProject project = malformedProject(claudeMdRule());

		BuildOutcome outcome = validate(project);

		assertFalse(outcome.succeeded(), outcome::describe);
		assertTrue(outcome.mentions(MISSING_AGENTS_REFERENCE), outcome::describe);
		for (String section : Set.of("## Java version", "## Maven", "## Principles for Java Development",
				"## Testing", "## Dependencies")) {
			assertTrue(outcome.mentions(MISSING_SECTION + section), outcome::describe);
		}
	}

	/**
	 * The severity switch exists so a team can adopt a rule before it is allowed to
	 * break the build, which is only true if the build really does go green while the
	 * violations are still visible in its log.
	 */
	@Test
	void severityWarnKeepsTheBuildGreenAndStillLogsTheViolations() {
		FixtureProject project = malformedProject(claudeMdRule("<severity>warn</severity>"));

		BuildOutcome outcome = validate(project);

		assertTrue(outcome.succeeded(), outcome::describe);
		assertTrue(outcome.mentions("[WARNING] CLAUDE.md is not well formed:"), outcome::describe);
		assertTrue(outcome.mentions(MISSING_SECTION + "## Dependencies"), outcome::describe);
	}

	/**
	 * The HTML report is written for a CI job to publish as an artifact, so what
	 * matters is that a real build leaves it on disk, at the configured path, holding
	 * both what failed and how to fix it.
	 */
	@Test
	void aConfiguredReportFileIsWrittenWhereACiJobCanPublishIt() {
		FixtureProject project = malformedProject(
				claudeMdRule("<reportFile>${project.basedir}/target/enforcer-report.html</reportFile>"));

		BuildOutcome outcome = validate(project);

		assertFalse(outcome.succeeded(), outcome::describe);
		Path report = project.path("target/enforcer-report.html");
		assertTrue(Files.isRegularFile(report), () -> "no report at " + report + ": " + outcome.describe());
		String html = project.read("target/enforcer-report.html");
		assertTrue(html.contains(MISSING_SECTION + "## Dependencies"), html);
		assertTrue(html.contains("How to fix"), html);
	}

	/**
	 * A baseline lets a rule be switched on over a backlog: the violations recorded
	 * once are suppressed, and only a new one fails the build. All three of those
	 * steps are builds — recording, passing with the backlog still in place, and
	 * failing on what was added afterwards — so all three are run here, against one
	 * baseline file the way a repository keeps one.
	 */
	@Test
	void aBaselineSuppressesTheRecordedViolationsUntilANewOneAppears() {
		FixtureProject project = malformedProject(claudeMdRule(
				"<baselineFile>${project.basedir}/enforcer-baseline.txt</baselineFile>",
				"<baseDir>${project.basedir}</baseDir>"));

		BuildOutcome recording = maven.run(project.root(), "-N", "validate",
				"-Dclaude.enforcer.writeBaseline=true");
		assertTrue(recording.succeeded(), recording::describe);
		assertTrue(Files.isRegularFile(project.path("enforcer-baseline.txt")), recording::describe);

		BuildOutcome suppressed = validate(project);
		assertTrue(suppressed.succeeded(), suppressed::describe);
		assertTrue(suppressed.mentions("violation(s) suppressed by the baseline"), suppressed::describe);

		project.write("CLAUDE.md", WRONGLY_TITLED_CLAUDE_MD);
		BuildOutcome regressed = validate(project);
		assertFalse(regressed.succeeded(), regressed::describe);
		assertTrue(regressed.mentions(WRONG_TITLE), regressed::describe);
		assertFalse(regressed.mentions(MISSING_SECTION + "## Dependencies"),
				() -> "a violation the baseline accepted must not be reported again: " + regressed.describe());
	}

	/**
	 * A document that is not there is a build-setup mistake, not a document-quality
	 * one, so it must fail even where the project asked for warnings only — otherwise
	 * a mistyped path would silently switch the check off and the build would stay
	 * green for as long as nobody looked.
	 */
	@Test
	void aMissingDocumentFailsTheBuildEvenWhenSeverityIsWarn() {
		FixtureProject project = project().enforcing(claudeMdRule("<severity>warn</severity>"));

		BuildOutcome outcome = validate(project);

		assertFalse(outcome.succeeded(), outcome::describe);
		assertTrue(outcome.mentions("CLAUDE.md does not exist at"), outcome::describe);
	}

	/**
	 * The other way a check can silently stop running: a parameter spelled almost
	 * right. Maven refuses to bind it, and it must stay that way — a
	 * {@code maxLineLenght} that were ignored would leave a project believing its
	 * line-length budget was enforced.
	 */
	@Test
	void aMisspelledParameterFailsTheBuildRatherThanSilentlyDisablingTheCheck() {
		FixtureProject project = wellFormedProject().enforcing(claudeMdRule("<maxLineLenght>80</maxLineLenght>"));

		BuildOutcome outcome = validate(project);

		assertFalse(outcome.succeeded(), outcome::describe);
		assertTrue(outcome.mentions("Cannot find 'maxLineLenght'"), outcome::describe);
	}

	private BuildOutcome validate(FixtureProject project) {
		return maven.run(project.root(), "-N", "validate");
	}

	private FixtureProject project() {
		return new FixtureProject(workspace, environment, enforcerPluginVersion);
	}

	private FixtureProject wellFormedProject() {
		return project().layOutWellFormedProject();
	}

	/** A project whose only problem is its CLAUDE.md, so one rule has everything to say. */
	private FixtureProject malformedProject(String rulesXml) {
		return wellFormedProject().write("CLAUDE.md", MALFORMED_CLAUDE_MD).enforcing(rulesXml);
	}

	/** The document rule alone, plus whatever shared configuration the test is about. */
	private String claudeMdRule(String... sharedConfiguration) {
		return """
									<claudeMdFormat>
										<claudeMdFile>${project.basedir}/CLAUDE.md</claudeMdFile>
										%s
									</claudeMdFormat>
				""".formatted(String.join(System.lineSeparator(), sharedConfiguration));
	}
}

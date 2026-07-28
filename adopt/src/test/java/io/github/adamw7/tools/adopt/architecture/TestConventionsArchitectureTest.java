package io.github.adamw7.tools.adopt.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.test.architecture.CommonTestConventions;

/**
 * Applies the shared {@link CommonTestConventions} to the adopt module's own
 * test code, plus the two rules this module needs for itself: its step tests
 * must not spawn real processes, and its integration tests must stay read-only
 * towards the real GitHub repositories they run against. Only test classes are
 * analysed via {@link ImportOption.OnlyIncludeTests}.
 */
@AnalyzeClasses(packages = TestConventionsArchitectureTest.TEST_PACKAGE, importOptions = ImportOption.OnlyIncludeTests.class)
public class TestConventionsArchitectureTest {

	static final String TEST_PACKAGE = "io.github.adamw7.tools.adopt";

	private static final String STEP_TEST_PACKAGE = "..adopt.step..";
	private static final String PROCESS_COMMAND_RUNNER = TEST_PACKAGE + ".command.ProcessCommandRunner";
	private static final String PUSH_STEP = TEST_PACKAGE + ".step.PushStep";
	private static final String PULL_REQUEST_STEP = TEST_PACKAGE + ".step.PullRequestStep";

	@ArchTest
	static final ArchTests commonTestConventions = ArchTests.in(CommonTestConventions.class);

	@ArchTest
	static final ArchRule stepTestsDoNotSpawnProcesses = noClasses()
			.that().resideInAPackage(STEP_TEST_PACKAGE)
			.should().dependOnClassesThat().haveFullyQualifiedName(PROCESS_COMMAND_RUNNER)
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder")
			.because("a step is tested through the recording CommandRunner; running the real git, claude or "
					+ "gh would make the test slow, machine-dependent, and able to touch a real repository");

	@ArchTest
	static final ArchRule integrationTestsNeitherPushNorOpenPullRequests = noClasses()
			.that().haveSimpleNameEndingWith("IT")
			.should().dependOnClassesThat().haveFullyQualifiedName(PUSH_STEP)
			.orShould().dependOnClassesThat().haveFullyQualifiedName(PULL_REQUEST_STEP)
			.because("the integration tests clone real GitHub repositories, so their pipeline stops at the "
					+ "branch step: a test that pushed or opened a pull request would write to repositories "
					+ "nobody asked it to write to")
			.allowEmptyShould(true);
}

package io.github.adamw7.tools.enforcer.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.test.architecture.CommonTestConventions;

/**
 * Applies the shared {@link CommonTestConventions} to the enforcer module's own
 * test code, so the constraints that keep the unit suite fast and honest cannot
 * be bypassed by how a test is written, plus the two rules this module needs for
 * itself: a real Maven build belongs to the {@code e2e} package, and only to an
 * integration test there. Unlike the production architecture rules, this class
 * analyses only the test classes via {@link ImportOption.OnlyIncludeTests}.
 */
@AnalyzeClasses(packages = TestConventionsArchitectureTest.TEST_PACKAGE, importOptions = ImportOption.OnlyIncludeTests.class)
public class TestConventionsArchitectureTest {

	static final String TEST_PACKAGE = "io.github.adamw7.tools.enforcer";

	private static final String E2E_PACKAGE = "..enforcer.e2e..";
	private static final String TESTABLE_ANNOTATION = "org.junit.platform.commons.annotation.Testable";

	@ArchTest
	static final ArchTests commonTestConventions = ArchTests.in(CommonTestConventions.class);

	@ArchTest
	static final ArchRule onlyTheEndToEndSupportRunsARealBuild = noClasses()
			.that().resideOutsideOfPackage(E2E_PACKAGE)
			.should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder")
			.because("a rule is unit-tested by calling execute() on it; forking Maven is what the e2e "
					+ "package exists for, and it is the one thing here that cannot fit the 5-second "
					+ "per-test timeout");

	@ArchTest
	static final ArchRule realBuildsRunAsIntegrationTests = methods()
			.that().areMetaAnnotatedWith(TESTABLE_ANNOTATION)
			.and().areDeclaredInClassesThat().resideInAPackage(E2E_PACKAGE)
			.should().beDeclaredInClassesThat().haveSimpleNameEndingWith("IT")
			.because("the e2e tests run real Maven builds, so they belong to failsafe and the "
					+ "integration-tests profile; named *Test they would run in every pull request build "
					+ "and blow its 120-second budget");
}

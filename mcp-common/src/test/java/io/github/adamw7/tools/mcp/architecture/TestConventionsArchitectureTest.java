package io.github.adamw7.tools.mcp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.Disabled;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Conventions enforced on the module's own test code, so the constraints that
 * keep the unit suite fast and honest cannot be bypassed by how a test is
 * written. Unlike the production architecture rules, this class analyses only
 * the test classes via {@link ImportOption.OnlyIncludeTests}.
 */
@AnalyzeClasses(packages = TestConventionsArchitectureTest.TEST_PACKAGE, importOptions = ImportOption.OnlyIncludeTests.class)
public class TestConventionsArchitectureTest {

	static final String TEST_PACKAGE = "io.github.adamw7.tools.mcp";

	private static final String TESTABLE_ANNOTATION = "org.junit.platform.commons.annotation.Testable";

	@ArchTest
	static final ArchRule testMethodsLiveInProperlyNamedClasses = methods()
			.that().areMetaAnnotatedWith(TESTABLE_ANNOTATION)
			.should().beDeclaredInClassesThat().haveSimpleNameEndingWith("Test")
			.orShould().beDeclaredInClassesThat().haveSimpleNameEndingWith("IT")
			.because("surefire only runs *Test classes and failsafe only runs *IT classes, "
					+ "so a test method in a differently named class silently never runs");

	@ArchTest
	static final ArchRule noDisabledTestMethods = noMethods()
			.should().beAnnotatedWith(Disabled.class)
			.because("a disabled test gives false confidence; delete it or fix what it guards");

	@ArchTest
	static final ArchRule noDisabledTestClasses = noClasses()
			.should().beAnnotatedWith(Disabled.class)
			.because("a disabled test gives false confidence; delete it or fix what it guards");

	@ArchTest
	static final ArchRule testsUseJunit5 = noClasses()
			.should().dependOnClassesThat().resideInAPackage("org.junit")
			.because("tests use JUnit 5 (org.junit.jupiter); the JUnit 4 API must not creep back in");

	@ArchTest
	static final ArchRule testsDoNotSleep = noClasses()
			.should().callMethod(Thread.class, "sleep", long.class)
			.orShould().callMethod(Thread.class, "sleep", long.class, int.class)
			.because("a test that sleeps is slow and flaky; wait on a condition instead");
}

package io.github.adamw7.tools.test.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules for the shared test scaffolding itself. Every module's
 * tests depend on this module's test-jar, so what it needs, every module must
 * supply, and how its pieces are meant to be used — rule libraries imported with
 * {@code ArchTests.in(...)}, assertions called statically — is pinned here
 * rather than left to each consumer to rediscover.
 * <p>
 * The module has no production code: everything lives in {@code src/test/java}
 * so ArchUnit stays test-scoped, which is why no {@code ImportOption} narrows
 * the analysis. The shared {@link CommonTestConventions} apply to it as they do
 * to any module's tests.
 */
@AnalyzeClasses(packages = TestCommonArchitectureTest.TEST_COMMON_PACKAGE)
public class TestCommonArchitectureTest {

	static final String TEST_COMMON_PACKAGE = "io.github.adamw7.tools.test";

	private static final String ARCHITECTURE_PACKAGE = "..test.architecture..";
	private static final String ARCHUNIT_PACKAGE = "com.tngtech.archunit..";

	@ArchTest
	static final ArchTests commonTestConventions = ArchTests.in(CommonTestConventions.class);

	@ArchTest
	static final ArchRule theScaffoldingNeedsOnlyJunitAndArchUnit = noClasses()
			.should().dependOnClassesThat().resideOutsideOfPackages("java..", "org.junit..", ARCHUNIT_PACKAGE,
					TEST_COMMON_PACKAGE + "..")
			.because("the test-jar is on every module's test class path, so a dependency here is one every module "
					+ "must declare; the rule libraries name log4j's Logger and protobuf's types by string for "
					+ "exactly that reason");

	@ArchTest
	static final ArchRule assertionsDoNotNeedArchUnit = noClasses()
			.that().resideOutsideOfPackage(ARCHITECTURE_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(ARCHUNIT_PACKAGE)
			.because("ArchUnit is test-scoped here and so never reaches a consumer: a module that only wants "
					+ "ExpectedFailures or TestFiles must not have to declare ArchUnit to call them");

	@ArchTest
	static final ArchRule assertionsAreStaticHelpers = classes()
			.that().resideOutsideOfPackage(ARCHITECTURE_PACKAGE)
			.and().areTopLevelClasses()
			.should().haveModifier(JavaModifier.FINAL)
			.andShould().haveOnlyPrivateConstructors()
			.because("a shared assertion is a static call a test makes, never a base class it extends; inheriting "
					+ "one helper would tie a test to every helper added to it later");

	@ArchTest
	static final ArchRule ruleLibrariesAreImportedNotRun = noClasses()
			.that().resideInAPackage(ARCHITECTURE_PACKAGE)
			.and().haveSimpleNameNotEndingWith("Test")
			.should().beAnnotatedWith(AnalyzeClasses.class)
			.because("a library's rules run against whatever the importing test's @AnalyzeClasses selects; one of "
					+ "its own would also run them here, against this module, where they assert nothing");

	@ArchTest
	static final ArchRule rulesAreConstants = fields()
			.that().areAnnotatedWith(ArchTest.class)
			.should().beStatic()
			.andShould().beFinal()
			.because("every module imports the same rule objects, so a reassignable one could be replaced by any "
					+ "test sharing the fork, changing what every later importer checks");
}

package io.github.adamw7.tools.adopt.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Conventions enforced on the module's own test code, mirroring the other
 * modules so the constraints that keep the unit suite fast and honest cannot be
 * bypassed. Only test classes are analysed via {@link ImportOption.OnlyIncludeTests}.
 */
@AnalyzeClasses(packages = TestConventionsArchitectureTest.TEST_PACKAGE, importOptions = ImportOption.OnlyIncludeTests.class)
public class TestConventionsArchitectureTest {

	static final String TEST_PACKAGE = "io.github.adamw7.tools.adopt";

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
			.orShould().callMethod(TimeUnit.class, "sleep", long.class)
			.because("a test that sleeps is slow and flaky; wait on a condition instead");

	@ArchTest
	static final ArchRule testsDoNotAccessStandardStreams = noClasses()
			.should().accessField(System.class, "out")
			.orShould().accessField(System.class, "err")
			.because("a test that prints to the console adds noise instead of asserting; assert on the value instead")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule beforeAllAndAfterAllMethodsAreStatic = methods()
			.that().areAnnotatedWith(BeforeAll.class)
			.or().areAnnotatedWith(AfterAll.class)
			.should().beStatic()
			.because("JUnit 5 runs @BeforeAll/@AfterAll once per class only when they are static; "
					+ "a non-static one fails at runtime unless the class opts into the PER_CLASS lifecycle")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule testMethodsAreRunnableByJunit = methods()
			.that().areMetaAnnotatedWith(TESTABLE_ANNOTATION)
			.should().notBePrivate()
			.andShould().notBeStatic()
			.because("JUnit 5 silently ignores a private or static test method, so it never runs");
}

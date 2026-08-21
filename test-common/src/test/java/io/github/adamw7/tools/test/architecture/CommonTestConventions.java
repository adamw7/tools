package io.github.adamw7.tools.test.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.parallel.Isolated;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The conventions every module enforces on its own test code, so the
 * constraints that keep the unit suite fast and honest cannot be bypassed by
 * how a test is written. A module imports them with
 * {@code @ArchTest static final ArchTests commonTestConventions = ArchTests.in(CommonTestConventions.class);}
 * from a test annotated
 * {@code @AnalyzeClasses(importOptions = ImportOption.OnlyIncludeTests.class)},
 * which is what narrows these rules to that module's test classes.
 *
 * @see ImportOption.OnlyIncludeTests
 */
public class CommonTestConventions {

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
			.because("tests use JUnit Jupiter (org.junit.jupiter); the JUnit 4 API must not creep back in");

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
			.because("Jupiter runs @BeforeAll/@AfterAll once per class only when they are static; "
					+ "a non-static one fails at runtime unless the class opts into the PER_CLASS lifecycle")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule testsWritingSystemPropertiesAreIsolated = noClasses()
			.that().haveSimpleNameEndingWith("Test")
			.and().areNotAnnotatedWith(Isolated.class)
			.should().callMethod(System.class, "setProperty", String.class, String.class)
			.orShould().callMethod(System.class, "clearProperty", String.class)
			.orShould().callMethod(System.class, "setProperties", Properties.class)
			.because("unit tests run a class at a time in parallel and a system property is JVM-wide: "
					+ "a class that writes one changes what every concurrently running test reads, "
					+ "so it must carry @Isolated and run alone. Only the surefire unit run is "
					+ "class-parallel, so the *IT classes failsafe runs one after another are exempt")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule testMethodsAreRunnableByJunit = methods()
			.that().areMetaAnnotatedWith(TESTABLE_ANNOTATION)
			.should().notBePrivate()
			.andShould().notBeStatic()
			.because("Jupiter silently ignores a private or static test method, so it never runs");
}

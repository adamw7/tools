package io.github.adamw7.tools.data.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.nio.file.Path;

import org.junit.jupiter.api.parallel.Isolated;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.data.source.file.PathValidator;
import io.github.adamw7.tools.test.architecture.CommonTestConventions;

/**
 * Applies the shared {@link CommonTestConventions} to the data module's own
 * test code, so the constraints that keep the unit suite fast and honest cannot
 * be bypassed by how a test is written. Unlike the production architecture
 * rules, this class analyses only the test classes via
 * {@link ImportOption.OnlyIncludeTests}.
 */
@AnalyzeClasses(packages = TestConventionsArchitectureTest.TEST_PACKAGE, importOptions = ImportOption.OnlyIncludeTests.class)
public class TestConventionsArchitectureTest {

	static final String TEST_PACKAGE = "io.github.adamw7.tools.data";

	@ArchTest
	static final ArchTests commonTestConventions = ArchTests.in(CommonTestConventions.class);

	/**
	 * The module's own answer to the shared rule on system properties. The allowed
	 * base directory is the other piece of JVM-wide state a test here can write, and
	 * it fails the same way: while one class points it at a {@code @TempDir}, every
	 * concurrently running test that opens a file source is refused the resource it
	 * reads, with a {@link SecurityException} that names a directory it never chose.
	 */
	@ArchTest
	static final ArchRule testsConfiningFileAccessAreIsolated = noClasses()
			.that().haveSimpleNameEndingWith("Test")
			.and().areNotAnnotatedWith(Isolated.class)
			.should().callMethod(PathValidator.class, "setAllowedBaseDir", Path.class)
			.orShould().callMethod(PathValidator.class, "clearAllowedBaseDir")
			.because("the allowed base directory is one field for the whole JVM, so a test that "
					+ "writes it must run alone; only the surefire unit run is class-parallel, "
					+ "so the *IT classes failsafe runs one after another are exempt")
			.allowEmptyShould(true);
}

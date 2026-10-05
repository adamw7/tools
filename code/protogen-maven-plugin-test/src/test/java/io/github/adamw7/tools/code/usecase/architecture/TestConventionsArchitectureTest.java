package io.github.adamw7.tools.code.usecase.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.github.adamw7.tools.test.architecture.CommonTestConventions;

/**
 * Applies the shared {@link CommonTestConventions} to this module's own test
 * code, so the constraints that keep the unit suite fast and honest cannot be
 * bypassed by how a test is written. Only the hand-written use-case tests are
 * analysed; the generated builders are {@link GeneratedBuildersArchitectureTest}'s.
 */
@AnalyzeClasses(packages = TestConventionsArchitectureTest.TEST_PACKAGE, importOptions = ImportOption.OnlyIncludeTests.class)
public class TestConventionsArchitectureTest {

	static final String TEST_PACKAGE = "io.github.adamw7.tools.code.usecase";

	@ArchTest
	static final ArchTests commonTestConventions = ArchTests.in(CommonTestConventions.class);
}

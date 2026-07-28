package io.github.adamw7.tools.code.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.github.adamw7.tools.test.architecture.CommonTestConventions;

/**
 * Applies the shared {@link CommonTestConventions} to the protogen module's own
 * test code, so the constraints that keep the unit suite fast and honest cannot
 * be bypassed by how a test is written. Unlike the production architecture
 * rules, this class analyses only the test classes via
 * {@link ImportOption.OnlyIncludeTests}.
 */
@AnalyzeClasses(packages = TestConventionsArchitectureTest.TEST_PACKAGE, importOptions = ImportOption.OnlyIncludeTests.class)
public class TestConventionsArchitectureTest {

	static final String TEST_PACKAGE = "io.github.adamw7.tools.code";

	@ArchTest
	static final ArchTests commonTestConventions = ArchTests.in(CommonTestConventions.class);
}

package io.github.adamw7.tools.markdown.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.github.adamw7.tools.test.architecture.CommonTestConventions;

/**
 * Applies the shared {@link CommonTestConventions} to this module's own test
 * code, so the constraints that keep the unit suite fast and honest cannot be
 * bypassed by how a test is written. Unlike the production architecture rules,
 * this class analyses only the test classes via
 * {@link ImportOption.OnlyIncludeTests}.
 */
@AnalyzeClasses(packages = "io.github.adamw7.tools.markdown", importOptions = ImportOption.OnlyIncludeTests.class)
public class TestConventionsArchitectureTest {

	@ArchTest
	static final ArchTests commonTestConventions = ArchTests.in(CommonTestConventions.class);
}

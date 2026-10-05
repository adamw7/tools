package io.github.adamw7.tools.grpc.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.github.adamw7.tools.test.architecture.CommonTestConventions;

/**
 * Applies the shared {@link CommonTestConventions} to the gRPC example's test
 * code, so the constraints that keep the unit suite fast and honest cannot be
 * bypassed by how a test is written. Unlike the other modules, everything here
 * is a test class — the example included — so
 * {@link ImportOption.OnlyIncludeTests} narrows nothing away but protoc's output.
 */
@AnalyzeClasses(packages = TestConventionsArchitectureTest.TEST_PACKAGE, importOptions = ImportOption.OnlyIncludeTests.class)
public class TestConventionsArchitectureTest {

	static final String TEST_PACKAGE = "io.github.adamw7.tools.grpc";

	@ArchTest
	static final ArchTests commonTestConventions = ArchTests.in(CommonTestConventions.class);
}

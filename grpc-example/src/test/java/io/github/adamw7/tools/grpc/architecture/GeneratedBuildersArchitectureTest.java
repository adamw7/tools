package io.github.adamw7.tools.grpc.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.github.adamw7.tools.test.architecture.GeneratedBuilderConventions;

/**
 * Holds the builders generated from {@code greeter.proto} to the shape the
 * plugin promises: {@code build()} only once every required field is set, and
 * nothing at run time beyond protobuf-java. The plugin writes into
 * {@code generated-test-sources}, hence {@link ImportOption.OnlyIncludeTests}.
 */
@AnalyzeClasses(packages = GeneratedBuildersArchitectureTest.BUILDERS_PACKAGE,
		importOptions = ImportOption.OnlyIncludeTests.class)
public class GeneratedBuildersArchitectureTest {

	/** The plugin's {@code outputpackage}, as this module's pom configures it. */
	static final String BUILDERS_PACKAGE = "io.github.adamw7.tools.grpc.builders";

	@ArchTest
	static final ArchTests generatedBuilderConventions = ArchTests.in(GeneratedBuilderConventions.class);
}

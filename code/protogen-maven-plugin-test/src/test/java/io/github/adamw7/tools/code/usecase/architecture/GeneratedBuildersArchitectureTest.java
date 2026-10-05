package io.github.adamw7.tools.code.usecase.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.github.adamw7.tools.test.architecture.CommonCodingConventions;
import io.github.adamw7.tools.test.architecture.GeneratedBuilderConventions;

/**
 * Holds the builders this module generates to the shape the plugin promises.
 * The use-case tests show that a builder works; these rules show that no
 * builder lets {@code build()} be reached early and that none needs the plugin
 * at run time, for every message shape the module's protos cover — proto2 and
 * proto3, required chains, {@code oneof}s, maps, groups and extensions.
 * <p>
 * The output is also held to {@link CommonCodingConventions}, so code nobody
 * writes by hand meets the same bar as code somebody does. The plugin writes
 * into {@code generated-test-sources}, hence {@link ImportOption.OnlyIncludeTests}.
 */
@AnalyzeClasses(packages = GeneratedBuildersArchitectureTest.GENERATED_PACKAGE,
		importOptions = ImportOption.OnlyIncludeTests.class)
public class GeneratedBuildersArchitectureTest {

	/** The plugin's {@code outputpackage}, as this module's pom configures it. */
	static final String GENERATED_PACKAGE = "org.output.generated";

	@ArchTest
	static final ArchTests generatedBuilderConventions = ArchTests.in(GeneratedBuilderConventions.class);

	@ArchTest
	static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);
}

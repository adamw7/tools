package io.github.adamw7.context.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.Properties;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.context.tree.ProjectTreeSerializer;
import io.github.adamw7.tools.test.architecture.CommonCodingConventions;
import io.github.adamw7.tools.test.architecture.CommonNamingConventions;

/**
 * Architecture rules for the context module. The context finder and project
 * tree form a reusable core; the {@code mcp} package is a delivery mechanism on
 * top of that core and the only place allowed to know the shared MCP
 * scaffolding. These rules keep that separation intact. Only production classes
 * are analysed; test classes are excluded via {@link ImportOption}.
 */
@AnalyzeClasses(packages = ContextArchitectureTest.CONTEXT_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class ContextArchitectureTest {

	static final String CONTEXT_PACKAGE = "io.github.adamw7.context";

	private static final String CONTEXT_ANY_PACKAGE = "io.github.adamw7.context..";
	private static final String MCP_PACKAGE = "..context.mcp..";
	private static final String MCP_COMMON_PACKAGE = "io.github.adamw7.tools.mcp..";

	@ArchTest
	static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);

	@ArchTest
	static final ArchTests commonNamingConventions = ArchTests.in(CommonNamingConventions.class);

	@ArchTest
	static final ArchRule coreDoesNotDependOnMcpAdapter = noClasses()
			.that().resideInAPackage(CONTEXT_ANY_PACKAGE)
			.and().resideOutsideOfPackage(MCP_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(MCP_PACKAGE)
			.because("the MCP adapter is a delivery mechanism on top of the context core, not a dependency of it");

	@ArchTest
	static final ArchRule onlyTheMcpAdapterKnowsTheScaffolding = noClasses()
			.that().resideOutsideOfPackage(MCP_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(MCP_COMMON_PACKAGE)
			.because("only the mcp delivery package builds on the shared MCP scaffolding");

	@ArchTest
	static final ArchRule packagesAreFreeOfCycles = slices()
			.matching("io.github.adamw7.context.(*)..")
			.should().beFreeOfCycles();

	@ArchTest
	static final ArchRule serializersImplementTheContract = classes()
			.that().haveSimpleNameEndingWith("Serializer")
			.and().areNotInterfaces()
			.should().beAssignableTo(ProjectTreeSerializer.class)
			.because("every concrete *Serializer must honour the ProjectTreeSerializer contract");

	@ArchTest
	static final ArchRule onlyTlsConfigurationMutatesJvmWideProperties = noClasses()
			.that().doNotHaveSimpleName("TlsConfiguration")
			.should().callMethod(System.class, "setProperty", String.class, String.class)
			.orShould().callMethod(System.class, "clearProperty", String.class)
			.orShould().callMethod(System.class, "setProperties", Properties.class)
			.because("JVM-wide system properties are global mutable state; the TLS hardening customiser is "
					+ "the one sanctioned place to set them, so a stray setProperty elsewhere cannot clobber "
					+ "the pinned TLS protocol and key-exchange configuration")
			.allowEmptyShould(true);
}

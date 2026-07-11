package io.github.adamw7.context.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.apache.logging.log4j.Logger;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

import io.github.adamw7.context.tree.ProjectTreeSerializer;

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
	static final ArchRule loggersArePrivateAndFinal = fields()
			.that().haveRawType(Logger.class)
			.should().bePrivate()
			.andShould().beFinal()
			.because("loggers are private, immutable collaborators owned by their declaring class");

	@ArchTest
	static final ArchRule abstractClassesAreNamedWithAbstractPrefix = classes()
			.that().areNotInterfaces()
			.and().areTopLevelClasses()
			.and().haveModifier(JavaModifier.ABSTRACT)
			.should().haveSimpleNameStartingWith("Abstract")
			.because("an Abstract prefix signals at a glance that a type is meant to be extended, not used directly");

	@ArchTest
	static final ArchRule exceptionsAreNamedConsistently = classes()
			.that().haveSimpleNameEndingWith("Exception")
			.should().beAssignableTo(Exception.class)
			.because("types named *Exception must actually be exceptions")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule noAccessToStandardStreams =
			GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

	@ArchTest
	static final ArchRule noGenericExceptionsAreThrown =
			GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

	@ArchTest
	static final ArchRule noJavaUtilLogging =
			GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}

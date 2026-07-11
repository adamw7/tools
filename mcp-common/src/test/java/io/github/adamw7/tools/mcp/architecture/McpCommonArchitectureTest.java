package io.github.adamw7.tools.mcp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.io.PrintStream;
import java.io.PrintWriter;

import org.apache.logging.log4j.Logger;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

import io.github.adamw7.tools.mcp.McpTool;

/**
 * Architecture rules for the shared MCP scaffolding module. They keep the
 * reusable transport wiring and tool SPI honest as the code evolves: the tool
 * contract stays an interface, logging goes through log4j2, and the module
 * never terminates or writes to the console of the server that embeds it. Only
 * production classes are analysed; test classes are excluded via
 * {@link ImportOption}.
 */
@AnalyzeClasses(packages = McpCommonArchitectureTest.MCP_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class McpCommonArchitectureTest {

	static final String MCP_PACKAGE = "io.github.adamw7.tools.mcp";

	@ArchTest
	static final ArchRule toolSpiIsAContract = classes()
			.that().haveSimpleName("McpTool")
			.should().beInterfaces()
			.because("McpTool is the tool SPI that concrete servers implement, not a concrete tool");

	@ArchTest
	static final ArchRule toolsImplementTheContract = classes()
			.that().haveSimpleNameEndingWith("Tool")
			.and().areNotInterfaces()
			.should().beAssignableTo(McpTool.class)
			.because("every concrete *Tool must honour the McpTool contract")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule loggersAreConstants = fields()
			.that().haveRawType(Logger.class)
			.should().bePrivate()
			.andShould().beStatic()
			.andShould().beFinal()
			.because("loggers are shared, immutable, class-scoped collaborators");

	@ArchTest
	static final ArchRule publicFieldsAreImmutable = fields()
			.that().arePublic()
			.should().beFinal()
			.because("a public field is part of the type's API surface and must not be reassignable")
			.allowEmptyShould(true);

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
	static final ArchRule scaffoldingDoesNotHaltTheJvm = noClasses()
			.should().callMethod(System.class, "exit", int.class)
			.because("shared scaffolding must never terminate the host server; it should throw instead");

	@ArchTest
	static final ArchRule noPrintStackTrace = noClasses()
			.should().callMethod(Throwable.class, "printStackTrace")
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintStream.class)
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintWriter.class)
			.because("failures must be reported through log4j2, never printed as a raw stack trace");

	@ArchTest
	static final ArchRule noAccessToStandardStreams =
			GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

	@ArchTest
	static final ArchRule noGenericExceptionsAreThrown =
			GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

	@ArchTest
	static final ArchRule noJavaUtilLogging =
			GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

	@ArchTest
	static final ArchRule noJodaTime =
			GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME;
}

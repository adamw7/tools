package io.github.adamw7.tools.code.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Optional;

import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.Mojo;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Architecture rules for the protogen Maven plugin. The {@code format} package
 * is a self-contained foundation for source-code formatting; the {@code gen}
 * package builds the generated builders on top of it. These rules keep that
 * one-directional dependency, and pin the conventions the plugin already
 * follows. Only production classes are analysed; test classes are excluded via
 * {@link ImportOption}.
 */
@AnalyzeClasses(packages = ProtogenArchitectureTest.CODE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class ProtogenArchitectureTest {

	static final String CODE_PACKAGE = "io.github.adamw7.tools.code";

	private static final String FORMAT_PACKAGE = "..code.format..";
	private static final String GEN_PACKAGE = "..code.gen..";

	@ArchTest
	static final ArchRule formatDoesNotDependOnGeneration = noClasses()
			.that().resideInAPackage(FORMAT_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(GEN_PACKAGE)
			.because("the format package is a reusable foundation and must not couple to the code generator that builds on it");

	@ArchTest
	static final ArchRule packagesAreFreeOfCycles = slices()
			.matching("io.github.adamw7.tools.code.(*)..")
			.should().beFreeOfCycles();

	@ArchTest
	static final ArchRule mojosImplementTheMojoContract = classes()
			.that().haveSimpleNameEndingWith("Mojo")
			.and().areNotInterfaces()
			.and().doNotHaveModifier(JavaModifier.ABSTRACT)
			.should().beAssignableTo(Mojo.class)
			.because("every concrete *Mojo is a Maven goal and must implement the Mojo contract");

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
	static final ArchRule mutableStaticStateIsVolatile = fields()
			.that().areStatic()
			.and().areNotFinal()
			.should().haveModifier(JavaModifier.VOLATILE)
			.because("a mutable static field is shared across every thread, so it must be volatile "
					+ "for its updates to publish safely")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule optionalFieldsAreForbidden = noFields()
			.should().haveRawType(Optional.class)
			.because("Optional models a possibly-absent return value, not a field; a field should hold "
					+ "the value itself and be null-checked, never wrapped in an Optional")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule noLegacyDateTimeApi = noClasses()
			.should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Calendar")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.GregorianCalendar")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.text.DateFormat")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.text.SimpleDateFormat")
			.because("date and time handling must use java.time, not the legacy Date/Calendar API")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule productionCodeLogsThroughLog4j = noClasses()
			.should().dependOnClassesThat().resideInAPackage("java.util.logging..")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.System$Logger")
			.because("logging must go through log4j2 so configuration stays in one place");

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
			.because("types named *Exception must actually be exceptions");

	@ArchTest
	static final ArchRule noPrintStackTrace = noClasses()
			.should().callMethod(Throwable.class, "printStackTrace")
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintStream.class)
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintWriter.class)
			.because("failures must be reported through log4j2, never printed as a raw stack trace");

	@ArchTest
	static final ArchRule pluginDoesNotHaltTheJvm = noClasses()
			.should().callMethod(System.class, "exit", int.class)
			.because("a Maven plugin must fail the build by throwing MojoExecutionException, never by terminating the JVM");

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

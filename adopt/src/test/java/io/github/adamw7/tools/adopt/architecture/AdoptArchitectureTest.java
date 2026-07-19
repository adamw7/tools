package io.github.adamw7.tools.adopt.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

import io.github.adamw7.tools.adopt.step.AdoptionStep;

/**
 * Architecture rules for the adopt module, enforced with ArchUnit so the
 * package layering and coding conventions cannot rot. Only production classes
 * are analysed; test classes are excluded via {@link ImportOption}.
 */
@AnalyzeClasses(packages = AdoptArchitectureTest.ADOPT_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class AdoptArchitectureTest {

	static final String ADOPT_PACKAGE = "io.github.adamw7.tools.adopt";

	private static final String COMMAND_PACKAGE = "..adopt.command..";
	private static final String STEP_PACKAGE = "..adopt.step..";

	@ArchTest
	static final ArchRule commandLayerDoesNotDependOnSteps = noClasses()
			.that().resideInAPackage(COMMAND_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(STEP_PACKAGE)
			.because("the command-runner layer must not know about the adoption steps that use it");

	@ArchTest
	static final ArchRule stepsImplementTheContract = classes()
			.that().resideInAPackage(STEP_PACKAGE)
			.and().haveSimpleNameEndingWith("Step")
			.and().areNotInterfaces()
			.and().doNotHaveModifier(JavaModifier.ABSTRACT)
			.should().beAssignableTo(AdoptionStep.class)
			.because("every concrete *Step must honour the AdoptionStep contract");

	@ArchTest
	static final ArchRule adoptionStepsResideInStepPackage = classes()
			.that().areAssignableTo(AdoptionStep.class)
			.and().areNotInterfaces()
			.should().resideInAPackage(STEP_PACKAGE)
			.because("every adoption step belongs to the step package that defines the pipeline");

	@ArchTest
	static final ArchRule abstractTypesArePrefixed = classes()
			.that().haveModifier(JavaModifier.ABSTRACT).and().areNotInterfaces()
			.should().haveSimpleNameStartingWith("Abstract")
			.because("abstract classes are named with an Abstract prefix for clarity");

	@ArchTest
	static final ArchRule packagesAreFreeOfCycles = slices()
			.matching("io.github.adamw7.tools.adopt.(*)..")
			.should().beFreeOfCycles();

	@ArchTest
	static final ArchRule loggersArePrivateStaticFinal = fields()
			.that().haveRawType(Logger.class)
			.should().bePrivate().andShould().beStatic().andShould().beFinal()
			.because("loggers are private static final by convention");

	@ArchTest
	static final ArchRule exceptionsAreNamedConsistently = classes()
			.that().haveSimpleNameEndingWith("Exception")
			.should().beAssignableTo(Exception.class)
			.because("types named *Exception must actually be exceptions")
			.allowEmptyShould(true);

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
	static final ArchRule noPrintStackTrace = noClasses()
			.should().callMethod(Throwable.class, "printStackTrace")
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintStream.class)
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintWriter.class)
			.because("failures must be reported through log4j2, never printed as a raw stack trace");

	@ArchTest
	static final ArchRule adoptionDoesNotHaltTheJvm = noClasses()
			.should().callMethod(System.class, "exit", int.class)
			.because("the adoption pipeline reports failure by throwing AdoptionException, never by terminating the JVM");

	@ArchTest
	static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

	@ArchTest
	static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

	@ArchTest
	static final ArchRule noGenericExceptionsThrown = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

	@ArchTest
	static final ArchRule noJodaTime = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME;
}

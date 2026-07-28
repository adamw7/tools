package io.github.adamw7.tools.test.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Optional;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * The coding conventions every module's production code follows, held in one
 * place so a module's architecture test states only what is specific to that
 * module. A module imports them with
 * {@code @ArchTest static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);}
 * and the importing test's {@code @AnalyzeClasses} decides which classes they
 * run against.
 * <p>
 * Rules here must hold for every module. A convention that only most modules
 * follow belongs in {@link CommonNamingConventions} or in the module's own test,
 * so an exemption stays visible rather than silently weakening the shared rule.
 * The logger rule names its type by string so this module needs no log4j
 * dependency of its own.
 */
public class CommonCodingConventions {

	private static final String LOG4J_LOGGER = "org.apache.logging.log4j.Logger";

	@ArchTest
	static final ArchRule loggersAreConstants = fields()
			.that().haveRawType(LOG4J_LOGGER)
			.should().bePrivate()
			.andShould().beStatic()
			.andShould().beFinal()
			.because("loggers are shared, immutable, class-scoped collaborators")
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
	static final ArchRule exceptionsAreNamedConsistently = classes()
			.that().haveSimpleNameEndingWith("Exception")
			.should().beAssignableTo(Exception.class)
			.because("types named *Exception must actually be exceptions")
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
	static final ArchRule productionCodeDoesNotHaltTheJvm = noClasses()
			.should().callMethod(System.class, "exit", int.class)
			.because("every module here is embedded in a host process — a Maven build, an MCP server, a "
					+ "caller's application — so failure is reported by throwing, never by terminating the JVM");

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

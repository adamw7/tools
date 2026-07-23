package io.github.adamw7.tools.enforcer.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Optional;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * Architecture rules for the enforcer module. They pin the layering that the
 * package structure already follows today: {@code text} is the foundation,
 * {@code rule} builds on it, and the feature packages ({@code definition},
 * {@code doc}, {@code mcp}, {@code settings}) build on {@code rule} without
 * reaching sideways into one another. Only production classes are analysed.
 */
@AnalyzeClasses(packages = EnforcerArchitectureTest.ENFORCER_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class EnforcerArchitectureTest {

	static final String ENFORCER_PACKAGE = "io.github.adamw7.tools.enforcer";

	@ArchTest
	static final ArchRule layersDependInOneDirection = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.layer("Text").definedBy("..enforcer.text..")
			.layer("Rule").definedBy("..enforcer.rule..")
			.layer("Definition").definedBy("..enforcer.definition..")
			.layer("Doc").definedBy("..enforcer.doc..")
			.layer("Mcp").definedBy("..enforcer.mcp..")
			.layer("Secret").definedBy("..enforcer.secret..")
			.layer("Settings").definedBy("..enforcer.settings..")
			.whereLayer("Text").mayNotAccessAnyLayer()
			.whereLayer("Rule").mayOnlyAccessLayers("Text")
			.whereLayer("Definition").mayOnlyAccessLayers("Rule", "Text")
			.whereLayer("Doc").mayOnlyAccessLayers("Rule", "Text")
			.whereLayer("Mcp").mayOnlyAccessLayers("Rule", "Text")
			.whereLayer("Secret").mayOnlyAccessLayers("Rule", "Text")
			.whereLayer("Settings").mayOnlyAccessLayers("Rule", "Text")
			.as("text is the foundation, rule builds on it, and the feature packages "
					+ "build on rule without depending on each other");

	@ArchTest
	static final ArchRule packagesAreFreeOfCycles = slices()
			.matching("io.github.adamw7.tools.enforcer.(*)..")
			.should().beFreeOfCycles();

	@ArchTest
	static final ArchRule concreteRulesHonourTheContract = classes()
			.that().haveSimpleNameEndingWith("Rule")
			.and().areNotInterfaces()
			.and().doNotHaveModifier(JavaModifier.ABSTRACT)
			.should().beAssignableTo(ClaudeCodeEnforcerRule.class)
			.because("every concrete *Rule is an enforcer rule and must extend the shared base");

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

	@ArchTest
	static final ArchRule noPrintStackTrace = noClasses()
			.should().callMethod(Throwable.class, "printStackTrace")
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintStream.class)
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintWriter.class)
			.because("failures must be reported through log4j2, never printed as a raw stack trace");

	@ArchTest
	static final ArchRule enforcerDoesNotHaltTheJvm = noClasses()
			.should().callMethod(System.class, "exit", int.class)
			.because("an enforcer rule reports violations by throwing, never by killing the JVM");
}

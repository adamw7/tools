package io.github.adamw7.tools.adopt.architecture;

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
	static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

	@ArchTest
	static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

	@ArchTest
	static final ArchRule noGenericExceptionsThrown = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
}

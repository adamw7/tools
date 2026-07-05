package io.github.adamw7.tools.code.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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
	static final ArchRule noAccessToStandardStreams =
			GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

	@ArchTest
	static final ArchRule noGenericExceptionsAreThrown =
			GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

	@ArchTest
	static final ArchRule noJavaUtilLogging =
			GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}

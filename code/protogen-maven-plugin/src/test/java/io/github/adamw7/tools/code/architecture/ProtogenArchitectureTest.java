package io.github.adamw7.tools.code.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.apache.maven.plugin.Mojo;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.test.architecture.CommonCodingConventions;
import io.github.adamw7.tools.test.architecture.CommonNamingConventions;

/**
 * Architecture rules for the protogen Maven plugin. The {@code format} package
 * is a self-contained foundation for source-code formatting; the {@code gen}
 * package builds the generated builders on top of it. These rules keep that
 * one-directional dependency, and pin the conventions the plugin already
 * follows. Only production classes are analysed; test classes are excluded via
 * {@link ImportOption}.
 *
 * <p>This is the one module whose production code does not log through log4j2.
 * It is a Maven plugin, so its output belongs on {@code AbstractMojo.getLog()},
 * which is what honours {@code -q} and {@code -X} and attributes a line to the
 * plugin in the reactor output. The shared
 * {@link CommonCodingConventions} rule bans {@code java.util.logging} and
 * {@code System.Logger} rather than requiring log4j2, so the Mojo log does not
 * weaken it; {@link #pluginLogsThroughTheMojoLog} states the narrower rule this
 * module follows instead, so log4j2 cannot creep back into the plugin jar.
 */
@AnalyzeClasses(packages = ProtogenArchitectureTest.CODE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class ProtogenArchitectureTest {

	static final String CODE_PACKAGE = "io.github.adamw7.tools.code";

	private static final String FORMAT_PACKAGE = "..code.format..";
	private static final String GEN_PACKAGE = "..code.gen..";

	@ArchTest
	static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);

	@ArchTest
	static final ArchTests commonNamingConventions = ArchTests.in(CommonNamingConventions.class);

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
	static final ArchRule pluginLogsThroughTheMojoLog = noClasses()
			.should().dependOnClassesThat().resideInAPackage("org.apache.logging.log4j..")
			.because("a Maven plugin reports through org.apache.maven.plugin.logging.Log, which honours -q and -X "
					+ "and attributes its output to the plugin; log4j2 from inside a plugin ignores all of that");

	@ArchTest
	static final ArchRule mojosImplementTheMojoContract = classes()
			.that().haveSimpleNameEndingWith("Mojo")
			.and().areNotInterfaces()
			.and().doNotHaveModifier(JavaModifier.ABSTRACT)
			.should().beAssignableTo(Mojo.class)
			.because("every concrete *Mojo is a Maven goal and must implement the Mojo contract");
}

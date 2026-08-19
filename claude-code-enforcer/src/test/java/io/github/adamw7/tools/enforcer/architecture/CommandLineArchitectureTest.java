package io.github.adamw7.tools.enforcer.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.io.PrintStream;
import java.io.PrintWriter;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

import io.github.adamw7.tools.test.architecture.CommonNamingConventions;

/**
 * The command line's own rules. It is analysed apart from the rest of the module
 * because one shared convention cannot hold here — {@code noAccessToStandardStreams}
 * — and {@link io.github.adamw7.tools.test.architecture.CommonCodingConventions}
 * is explicit that a rule which does not hold for every module must be exempted
 * visibly rather than relaxed for everyone. This is that exemption, and it is
 * narrow: the streams are an entry point's interface to whoever ran it, so exactly
 * one class may reach them.
 *
 * <p>Everything else the shared conventions ask still applies and is restated
 * here, so leaving the package out of the shared import costs it no checking:
 * failure is still reported by throwing rather than by ending the JVM, a stack
 * trace is still never printed raw, and logging still does not reach for another
 * framework.
 *
 * @see EnforcerArchitectureTest.WithoutTheCommandLine
 */
@AnalyzeClasses(packages = CommandLineArchitectureTest.CLI_PACKAGE,
		importOptions = ImportOption.DoNotIncludeTests.class)
public class CommandLineArchitectureTest {

	static final String CLI_PACKAGE = "io.github.adamw7.tools.enforcer.cli";

	@ArchTest
	static final ArchTests commonNamingConventions = ArchTests.in(CommonNamingConventions.class);

	@ArchTest
	static final ArchRule onlyTheEntryPointReachesTheStandardStreams = noClasses()
			.that().haveSimpleNameNotEndingWith("Main")
			.and().haveSimpleNameNotEndingWith("PrintingEnforcerLogger")
			.should().accessField(System.class, "out")
			.orShould().accessField(System.class, "err")
			.because("the streams are how a command line answers whoever ran it, which is Main's job and "
					+ "the logger's it hands them to; anywhere else they would be a check printing behind "
					+ "the back of the logger every rule already reports through");

	@ArchTest
	static final ArchRule entryPointsAreNamedMain = methods()
			.that().haveName("main").and().arePublic().and().areStatic()
			.should().beDeclaredInClassesThat().haveSimpleName("Main")
			.because("the module ships one entry point and the manifest names it by class, so a main method "
					+ "anywhere else is unreachable in practice");

	@ArchTest
	static final ArchRule theCommandLineDoesNotHaltTheJvm = noClasses()
			.should().callMethod(System.class, "exit", int.class)
			.because("a failed check is reported by throwing, as everywhere else here: the process exits "
					+ "non-zero either way, and a class that ends a JVM cannot be called by anything but a "
					+ "shell");

	@ArchTest
	static final ArchRule noPrintStackTrace = noClasses()
			.should().callMethod(Throwable.class, "printStackTrace")
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintStream.class)
			.orShould().callMethod(Throwable.class, "printStackTrace", PrintWriter.class)
			.because("a raw stack trace is not a report; the violations are printed and the failure thrown");

	@ArchTest
	static final ArchRule noJavaUtilLogging = noClasses()
			.should().dependOnClassesThat().resideInAPackage("java.util.logging..")
			.because("this module carries no logging framework at all, which is what keeps one off the "
					+ "plugin class path of every build that wires a rule");

	@ArchTest
	static final ArchRule noGenericExceptionsAreThrown =
			GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

	/**
	 * The command line reaches the rules only through the composite, which is the
	 * layering {@link EnforcerArchitectureTest} pins for everything below it. Stated
	 * here because the package is analysed apart from that test.
	 */
	@ArchTest
	static final ArchRule theCommandLineReachesTheRulesThroughTheComposite = noClasses()
			.that().resideInAPackage(CLI_PACKAGE)
			.should().dependOnClassesThat().resideInAnyPackage("..enforcer.definition..", "..enforcer.doc..",
					"..enforcer.mcp..", "..enforcer.okf..", "..enforcer.secret..", "..enforcer.settings..")
			.because("what the command line runs is the composite's to decide, so a part named here would "
					+ "be a second catalogue to keep in step with it");

	@ArchTest
	static final ArchRule theCommandLineIsSmall = classes()
			.that().resideInAPackage(CLI_PACKAGE).and().areTopLevelClasses()
			.should().bePackagePrivate()
			.orShould().haveSimpleName("Main")
			.because("the command line is a way to run the rules, not an API: only the entry point the "
					+ "manifest names is visible outside it");
}

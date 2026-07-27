package io.github.adamw7.tools.adopt.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.io.IOException;
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

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.BuildSystem;
import io.github.adamw7.tools.mcp.McpTool;

/**
 * Architecture rules for the adopt module, enforced with ArchUnit so the
 * package layering and coding conventions cannot rot. The pipeline is a plain
 * library: the {@code step} package holds the stages, the {@code command}
 * package is the only place a process is spawned, and the {@code mcp} package is
 * a delivery mechanism on top of both. Only production classes are analysed;
 * test classes are excluded via {@link ImportOption}.
 */
@AnalyzeClasses(packages = AdoptArchitectureTest.ADOPT_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class AdoptArchitectureTest {

	static final String ADOPT_PACKAGE = "io.github.adamw7.tools.adopt";

	private static final String ADOPT_ANY_PACKAGE = "io.github.adamw7.tools.adopt..";
	private static final String COMMAND_PACKAGE = "..adopt.command..";
	private static final String STEP_PACKAGE = "..adopt.step..";
	private static final String MCP_PACKAGE = "..adopt.mcp..";
	private static final String MCP_COMMON_PACKAGE = "io.github.adamw7.tools.mcp..";
	private static final String SPRING_PACKAGE = "org.springframework..";

	private static final String CLONE_STEP = ADOPT_PACKAGE + ".step.CloneStep";
	private static final String PROCESS_COMMAND_RUNNER = ADOPT_PACKAGE + ".command.ProcessCommandRunner";

	@ArchTest
	static final ArchRule commandLayerDoesNotDependOnSteps = noClasses()
			.that().resideInAPackage(COMMAND_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(STEP_PACKAGE)
			.because("the command-runner layer must not know about the adoption steps that use it");

	@ArchTest
	static final ArchRule coreDoesNotDependOnMcpAdapter = noClasses()
			.that().resideInAPackage(ADOPT_ANY_PACKAGE)
			.and().resideOutsideOfPackage(MCP_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(MCP_PACKAGE)
			.because("the MCP adapter is a delivery mechanism on top of the adoption pipeline, not a dependency of it");

	@ArchTest
	static final ArchRule onlyTheMcpAdapterKnowsTheScaffolding = noClasses()
			.that().resideOutsideOfPackage(MCP_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(MCP_COMMON_PACKAGE)
			.because("only the mcp delivery package builds on the shared MCP scaffolding");

	@ArchTest
	static final ArchRule onlyTheMcpAdapterKnowsSpring = noClasses()
			.that().resideOutsideOfPackage(MCP_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(SPRING_PACKAGE)
			.because("Spring Boot hosts the MCP server; the pipeline itself stays a plain library that the "
					+ "command line runs without a container");

	@ArchTest
	static final ArchRule mcpToolsImplementTheContract = classes()
			.that().resideInAPackage(MCP_PACKAGE)
			.and().haveSimpleNameEndingWith("Tool")
			.should().beAssignableTo(McpTool.class)
			.because("a *Tool in the mcp package is what the server exposes, so it must honour the McpTool SPI");

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
	static final ArchRule adoptionStepsAreNamedStep = classes()
			.that().areAssignableTo(AdoptionStep.class)
			.and().areNotInterfaces()
			.should().haveSimpleNameEndingWith("Step")
			.because("the pipeline reads as the list of steps it runs, so every implementation carries the "
					+ "suffix that says it is one");

	@ArchTest
	static final ArchRule buildSystemsImplementTheContract = classes()
			.that().haveSimpleNameEndingWith("BuildSystem")
			.and().areNotInterfaces()
			.should().beAssignableTo(BuildSystem.class)
			.andShould().resideInAPackage(STEP_PACKAGE)
			.because("supporting a new build tool means adding a BuildSystem beside the others, "
					+ "never branching on the build tool inside a step");

	@ArchTest
	static final ArchRule commandRunnersImplementTheContract = classes()
			.that().haveSimpleNameEndingWith("CommandRunner")
			.and().areNotInterfaces()
			.should().beAssignableTo(CommandRunner.class)
			.andShould().resideInAPackage(COMMAND_PACKAGE)
			.because("the runner is the single seam every step shells out through; an implementation "
					+ "outside that contract would bypass the redaction and timeout it applies");

	@ArchTest
	static final ArchRule processExecutionStaysInTheCommandPackage = noClasses()
			.that().resideOutsideOfPackage(COMMAND_PACKAGE)
			.should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.Process")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessHandle")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.Runtime")
			.because("spawning a process is the command package's job alone; every other class asks a "
					+ "CommandRunner, which is what makes the pipeline testable without real git, claude and gh");

	@ArchTest
	static final ArchRule stepsShellOutThroughTheRunnerContract = noClasses()
			.that().resideInAPackage(STEP_PACKAGE)
			.should().dependOnClassesThat().haveFullyQualifiedName(PROCESS_COMMAND_RUNNER)
			.because("a step is handed the CommandRunner to use, so tests drive it with a recording stub; "
					+ "reaching for the process-spawning implementation would make it untestable");

	@ArchTest
	static final ArchRule stepsDoNotDependOnThePipelineThatRunsThem = noClasses()
			.that().resideInAPackage(STEP_PACKAGE)
			.should().dependOnClassesThat().haveFullyQualifiedName(ADOPT_PACKAGE + ".GitHubRepoAdopter")
			.orShould().dependOnClassesThat().haveFullyQualifiedName(ADOPT_PACKAGE + ".BatchAdoption")
			.orShould().dependOnClassesThat().haveFullyQualifiedName(ADOPT_PACKAGE + ".CliArguments")
			.orShould().dependOnClassesThat().haveFullyQualifiedName(ADOPT_PACKAGE + ".Main")
			.because("steps are ordered and assembled from outside; one that reached back to the adopter, "
					+ "the batch, or the command line could no longer be reordered or reused");

	@ArchTest
	static final ArchRule onlyTheCloneStepReadsTheCredentialledUrl = noClasses()
			.that().doNotHaveFullyQualifiedName(CLONE_STEP)
			.should().callMethod(AdoptionContext.class, "repositoryUrl")
			.because("repositoryUrl() answers the clone URL with its credentials intact; only the git clone "
					+ "command may see them, and everything else reads the redacted displayUrl()");

	@ArchTest
	static final ArchRule adoptionReachesGitHubThroughItsTools = noClasses()
			.that().resideOutsideOfPackage(MCP_PACKAGE)
			.should().dependOnClassesThat().resideInAnyPackage("java.net..", "javax.net..")
			.because("the adoption never speaks a network protocol itself: it shells out to git and gh, which "
					+ "carry the operator's own credentials, host keys, and proxy configuration");

	@ArchTest
	static final ArchRule stepStateIsImmutable = fields()
			.that().areDeclaredInClassesThat().resideInAPackage(STEP_PACKAGE)
			.should().beFinal()
			.because("one step instance adopts every repository of a batch, so a mutable field would leak "
					+ "one repository's state into the next");

	@ArchTest
	static final ArchRule entryPointsAreNamedMain = methods()
			.that().haveName("main").and().arePublic().and().areStatic()
			.should().beDeclaredInClassesThat().haveSimpleName("Main")
			.because("the module ships two entry points — the pipeline CLI and the MCP server — and the pom "
					+ "names both by class, so a main method anywhere else is unreachable in practice");

	@ArchTest
	static final ArchRule publicApiDoesNotDeclareCheckedExceptions = noMethods()
			.that().arePublic()
			.should().declareThrowableOfType(IOException.class)
			.because("the pipeline reports failure with the unchecked AdoptionException, so a caller "
					+ "assembling steps is never made to translate an IOException step by step");

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

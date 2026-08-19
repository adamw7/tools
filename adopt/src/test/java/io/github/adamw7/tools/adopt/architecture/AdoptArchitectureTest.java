package io.github.adamw7.tools.adopt.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.io.IOException;
import java.time.Duration;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;
import io.github.adamw7.tools.adopt.command.RetryingCommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;
import io.github.adamw7.tools.adopt.step.BuildSystem;
import io.github.adamw7.tools.mcp.McpTool;
import io.github.adamw7.tools.test.architecture.CommonCodingConventions;
import io.github.adamw7.tools.test.architecture.CommonNamingConventions;

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
	private static final String PUSH_STEP = ADOPT_PACKAGE + ".step.PushStep";
	private static final String PROCESS_COMMAND_RUNNER = ADOPT_PACKAGE + ".command.ProcessCommandRunner";
	private static final String COMMAND_RUNNERS = ADOPT_PACKAGE + ".command.CommandRunners";

	@ArchTest
	static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);

	@ArchTest
	static final ArchTests commonNamingConventions = ArchTests.in(CommonNamingConventions.class);

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
	static final ArchRule onlyTheCloneAndPushStepsReadTheCredentialledUrl = noClasses()
			.that().doNotHaveFullyQualifiedName(CLONE_STEP).and().doNotHaveFullyQualifiedName(PUSH_STEP)
			.should().callMethod(AdoptionContext.class, "repositoryUrl")
			.because("repositoryUrl() answers the clone URL with its credentials intact; only the commands "
					+ "that authenticate to the remote may see them — the clone, the fetch that resumes a "
					+ "checkout, and the push, the last two supplying them per invocation because the clone no "
					+ "longer leaves them in .git/config — and everything else reads the redacted displayUrl() "
					+ "or the credential-free checkoutUrl()");

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

	/**
	 * The counterpart of {@link #onlyTheAdopterAssemblesThePipeline}, one layer down:
	 * the adopter is handed a runner, and the runner a real run is driven through is
	 * assembled in one place too — {@value #COMMAND_RUNNERS}. Both entry points want
	 * the same two decorated halves, and one that assembled its own would be a
	 * {@code --timeout} or a {@code --retries} the other silently answers
	 * differently: a drift nothing downstream reports, since a run that lost the
	 * retry decorator simply stops retrying.
	 */
	@ArchTest
	static final ArchRule onlyTheCommandPackageAssemblesTheToolchain = noClasses()
			.that().resideOutsideOfPackage(COMMAND_PACKAGE)
			.should().callConstructor(ProcessCommandRunner.class, Duration.class)
			.orShould().callConstructor(RetryingCommandRunner.class, CommandRunner.class, int.class)
			.because("the command line and the MCP tool must drive a run through one toolchain, assembled by "
					+ COMMAND_RUNNERS + ", so neither can bound a command or retry a refused one on terms of its own");

	@ArchTest
	static final ArchRule onlyTheAdopterAssemblesThePipeline = noClasses()
			.that().resideOutsideOfPackage(STEP_PACKAGE)
			.and().doNotHaveFullyQualifiedName(ADOPT_PACKAGE + ".GitHubRepoAdopter")
			.should().dependOnClassesThat().areAssignableTo(AdoptionStep.class)
			.because("the adopter is the single place the ordered pipeline is built, so the command line "
					+ "and the MCP tool cannot drift into adopting a repository two different ways");

	@ArchTest
	static final ArchRule buildSystemsDescribeCommandsRatherThanRunThem = noClasses()
			.that().areAssignableTo(BuildSystem.class)
			.should().dependOnClassesThat().areAssignableTo(CommandRunner.class)
			.because("a BuildSystem answers what a project's own build tool is called and how it is "
					+ "invoked; the step it answers runs it, which is what keeps adding a build tool a "
					+ "matter of adding a class rather than editing the steps");

	@ArchTest
	static final ArchRule onlyTheCommandPackageReadsTheEnvironment = noClasses()
			.that().resideOutsideOfPackage(COMMAND_PACKAGE)
			.should().callMethod(System.class, "getenv")
			.orShould().callMethod(System.class, "getenv", String.class)
			.because("a run is driven by the options it was given, not by the ambient environment; the one "
					+ "exception is resolving an executable on PATH, which lives behind the command runner");

	@ArchTest
	static final ArchRule onlyTheCommandPackageAndTheBatchNeedConcurrency = noClasses()
			.that().resideOutsideOfPackage(COMMAND_PACKAGE)
			.and().doNotHaveFullyQualifiedName(ADOPT_PACKAGE + ".BatchAdoption")
			.and().doNotHaveFullyQualifiedName(ADOPT_PACKAGE + ".Checkouts")
			.should().dependOnClassesThat().resideInAPackage("java.util.concurrent..")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.Thread")
			.because("one repository's adoption is a sequential pipeline whose steps depend on the one "
					+ "before, and a step that reached for a thread would be doing something the pipeline "
					+ "does not do. Two places legitimately do: spawning a process, which pumps its output "
					+ "while a timeout runs, and the batch, whose repositories are independent of each "
					+ "other by construction — Checkouts is named with it because the claim that keeps them "
					+ "independent is what several threads contend on");

	@ArchTest
	static final ArchRule packagesAreFreeOfCycles = slices()
			.matching("io.github.adamw7.tools.adopt.(*)..")
			.should().beFreeOfCycles();
}

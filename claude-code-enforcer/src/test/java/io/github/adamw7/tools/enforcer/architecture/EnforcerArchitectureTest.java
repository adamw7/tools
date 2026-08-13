package io.github.adamw7.tools.enforcer.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.nio.file.Files;
import java.util.Locale;

import javax.inject.Named;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.test.architecture.CommonCodingConventions;

/**
 * Architecture rules for the enforcer module. They pin the layering that the
 * package structure already follows today: {@code text} is the foundation,
 * {@code rule} builds on it, and the feature packages ({@code definition},
 * {@code doc}, {@code mcp}, {@code settings}) build on {@code rule} without
 * reaching sideways into one another. They also pin what a rule is allowed to
 * do at build time: read the project, and nothing else — no process, no network,
 * and no write outside the report, the baseline and the front-matter fix a rule
 * was asked for. Only production classes are analysed.
 * <p>
 * The Markdown reader itself lives in {@code markdown-common} and is held to its
 * own rules there, because the {@code adopt} pipeline reads documents through the
 * same one and cannot depend on this module to get it.
 */
@AnalyzeClasses(packages = EnforcerArchitectureTest.ENFORCER_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class EnforcerArchitectureTest {

	static final String ENFORCER_PACKAGE = "io.github.adamw7.tools.enforcer";

	private static final String RULE_SUFFIX = "Rule";
	private static final String TEXT_PACKAGE = "..enforcer.text..";
	private static final String OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper";
	private static final String JSON_NODES = ENFORCER_PACKAGE + ".rule.JsonNodes";
	private static final String BASELINE = ENFORCER_PACKAGE + ".rule.Baseline";
	private static final String HTML_REPORT = ENFORCER_PACKAGE + ".rule.HtmlReport";
	private static final String FILE_MUTATIONS =
			"write|writeString|newBufferedWriter|newOutputStream|createFile|createDirectory|createDirectories"
					+ "|delete|deleteIfExists|move|copy";

	@ArchTest
	static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);

	@ArchTest
	static final ArchRule layersDependInOneDirection = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.layer("Text").definedBy("..enforcer.text..")
			.layer("Rule").definedBy("..enforcer.rule..")
			.layer("Definition").definedBy("..enforcer.definition..")
			.layer("Doc").definedBy("..enforcer.doc..")
			.layer("Mcp").definedBy("..enforcer.mcp..")
			.layer("Okf").definedBy("..enforcer.okf..")
			.layer("Secret").definedBy("..enforcer.secret..")
			.layer("Settings").definedBy("..enforcer.settings..")
			.whereLayer("Text").mayNotAccessAnyLayer()
			.whereLayer("Rule").mayOnlyAccessLayers("Text")
			.whereLayer("Definition").mayOnlyAccessLayers("Rule", "Text")
			.whereLayer("Doc").mayOnlyAccessLayers("Rule", "Text")
			.whereLayer("Mcp").mayOnlyAccessLayers("Rule", "Text")
			.whereLayer("Okf").mayOnlyAccessLayers("Rule", "Text")
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
			.that().haveSimpleNameEndingWith(RULE_SUFFIX)
			.and().areNotInterfaces()
			.and().doNotHaveModifier(JavaModifier.ABSTRACT)
			.should().beAssignableTo(ClaudeCodeEnforcerRule.class)
			.because("every concrete *Rule is an enforcer rule and must extend the shared base");

	@ArchTest
	static final ArchRule concreteRulesAreDiscoverable = classes()
			.that().areAssignableTo(ClaudeCodeEnforcerRule.class)
			.and().doNotHaveModifier(JavaModifier.ABSTRACT)
			.should().beAnnotatedWith(Named.class)
			.andShould().bePublic()
			.because("maven-enforcer resolves a configured rule through the Sisu index, which only lists "
					+ "public @Named types; an unannotated rule compiles and tests green, then fails the "
					+ "build that configures it with 'rule not found'");

	@ArchTest
	static final ArchRule rulesCarryTheRuleSuffix = classes()
			.that().areAssignableTo(ClaudeCodeEnforcerRule.class)
			.should().haveSimpleNameEndingWith(RULE_SUFFIX)
			.because("the name a pom configures is derived from the class name, so a rule that dropped the "
					+ "suffix would be configured as something no reader could predict");

	@ArchTest
	static final ArchRule ruleNamesFollowTheClassName = classes()
			.that().areAssignableTo(ClaudeCodeEnforcerRule.class)
			.and().haveSimpleNameEndingWith(RULE_SUFFIX)
			.and().doNotHaveModifier(JavaModifier.ABSTRACT)
			.should(beNamedAfterTheirClass())
			.because("a pom, CLAUDE.md and AGENTS.md all name a rule by its @Named value, so deriving it "
					+ "from the class name keeps the one string a reader has to guess guessable");

	@ArchTest
	static final ArchRule abstractTypesAreNamedRule = classes()
			.that().areNotInterfaces()
			.and().areTopLevelClasses()
			.and().haveModifier(JavaModifier.ABSTRACT)
			.should().haveSimpleNameEndingWith(RULE_SUFFIX)
			.because("this module trades the repository-wide Abstract prefix for names the poms configure, "
					+ "so an abstract type here is a rule base; an abstract helper would need the prefix and "
					+ "the shared naming convention back");

	@ArchTest
	static final ArchRule theTextFoundationIsFrameworkFree = noClasses()
			.that().resideInAPackage(TEXT_PACKAGE)
			.should().dependOnClassesThat().resideInAnyPackage("org.apache.maven..", "javax.inject..",
					"com.fasterxml..")
			.because("text reads and rewrites markdown for whoever asks; keeping the enforcer API, its "
					+ "injection annotations and its JSON parser out of it is what lets a rule be tested "
					+ "without a Maven session");

	@ArchTest
	static final ArchRule oneConfiguredJsonMapper = noClasses()
			.that().doNotHaveFullyQualifiedName(JSON_NODES)
			.should().dependOnClassesThat().haveFullyQualifiedName(OBJECT_MAPPER)
			.because("JsonNodes owns the single mapper configured for the comments and trailing commas "
					+ "Claude Code's JSON files allow; a rule that built its own would reject a file the "
					+ "tool itself accepts");

	@ArchTest
	static final ArchRule rulesDoNotSpawnProcesses = noClasses()
			.should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.Process")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.Runtime")
			.because("hookCommandsValid and permissionsFormat read commands a repository asked Claude Code "
					+ "to run; the whole point of checking them at build time is that this module never "
					+ "runs one");

	@ArchTest
	static final ArchRule rulesDoNotReachTheNetwork = noClasses()
			.should().dependOnClassesThat().resideInAnyPackage("java.net.http..", "javax.net..")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.URL")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.URLConnection")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.Socket")
			.because("the rules scan a working copy for secrets and malformed configuration, so they must "
					+ "stay offline: a build behind a firewall runs them, and a check that could send what "
					+ "it reads would be the leak noSecrets exists to prevent")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule rulesDoNotWriteToTheProjectTheyCheck = noClasses()
			.that().doNotHaveFullyQualifiedName(BASELINE)
			.and().doNotHaveFullyQualifiedName(HTML_REPORT)
			.should().callMethodWhere(target(owner(type(Files.class))).and(target(nameMatching(FILE_MUTATIONS))))
			.because("a check reports what it found; the two places that write here are the ones a build "
					+ "asked for — the HTML report and the recorded baseline. The front-matter fix writes "
					+ "through markdown-common's MarkdownText, which its own module holds to the same rule")
			.allowEmptyShould(true);

	/**
	 * Holds a rule's {@code @Named} value to the name derived from its class:
	 * the simple name without its {@code Rule} suffix, decapitalised. Rules
	 * lacking the annotation are left to {@link #concreteRulesAreDiscoverable},
	 * which reports the missing annotation as the real problem.
	 */
	private static ArchCondition<JavaClass> beNamedAfterTheirClass() {
		return new ArchCondition<>("be named after their class") {

			@Override
			public void check(JavaClass rule, ConditionEvents events) {
				rule.tryGetAnnotationOfType(Named.class)
						.ifPresent(named -> events.add(matches(rule, named.value())));
			}

			private SimpleConditionEvent matches(JavaClass rule, String configuredName) {
				String expected = nameDerivedFrom(rule.getSimpleName());
				return new SimpleConditionEvent(rule, expected.equals(configuredName),
						rule.getName() + " is configured as <" + configuredName + "> instead of <" + expected + ">");
			}
		};
	}

	private static String nameDerivedFrom(String simpleName) {
		String withoutSuffix = simpleName.substring(0, simpleName.length() - RULE_SUFFIX.length());
		return withoutSuffix.substring(0, 1).toLowerCase(Locale.ROOT) + withoutSuffix.substring(1);
	}
}

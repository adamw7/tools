package io.github.adamw7.tools.test.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.onlyHaveDependenciesWhere;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The shape of what {@code protogen-maven-plugin} generates, checked on the
 * compiled output rather than on the text the generator writes. A message with
 * required fields becomes a chain of stages — {@code PersonBuilder}, one
 * {@code Person<Field>Ifc} per required field, and finally
 * {@code PersonOptionalIfc} — and the compile-time guarantee the plugin exists
 * for is that {@code build()} sits only on that last stage.
 * <p>
 * A module that runs the plugin imports these rules with
 * {@code @ArchTest static final ArchTests generatedBuilderConventions = ArchTests.in(GeneratedBuilderConventions.class);}
 * from a test whose {@code @AnalyzeClasses} names only the plugin's configured
 * {@code outputpackage}, so every rule may assume what it sees is generated. The
 * plugin writes into {@code generated-test-sources}, so that test imports
 * {@code ImportOption.OnlyIncludeTests}.
 * <p>
 * Unlike the repository-wide libraries, no rule here allows an empty
 * {@code should}: a generation step that silently produced nothing fails these
 * rules instead of passing them. The protobuf types are named by string so this
 * module needs no protobuf dependency of its own.
 */
public class GeneratedBuilderConventions {

	private static final String STAGE_SUFFIX = "Ifc";
	private static final String OPTIONAL_STAGE_SUFFIX = "OptionalIfc";
	private static final String MESSAGE_OR_BUILDER = "com.google.protobuf.MessageLiteOrBuilder";
	private static final String PROTOBUF_ENUM = "com.google.protobuf.Internal$EnumLite";

	@ArchTest
	static final ArchRule onlyTheOptionalStageBuilds = methods()
			.that().haveName("build")
			.should().beDeclaredInClassesThat().areAssignableTo(simpleNameEndingWith(OPTIONAL_STAGE_SUFFIX))
			.because("build() must stay unreachable until every required field is set; a required-field stage "
					+ "that offered it would compile a message missing that field, which protobuf's own build() "
					+ "then rejects only at run time — the very gap the generated chain closes");

	@ArchTest
	static final ArchRule stageContractsAreInterfaces = classes()
			.that().haveSimpleNameEndingWith(STAGE_SUFFIX)
			.should().beInterfaces()
			.because("each stage of the chain is typed by its interface, so a setter hands back the next stage "
					+ "without exposing the class that implements it");

	@ArchTest
	static final ArchRule everyGeneratedClassIsAStage = classes()
			.that().areNotInterfaces()
			.should().beAssignableTo(simpleNameEndingWith(STAGE_SUFFIX))
			.because("a builder or implementation outside the stage contracts would be a way into the chain "
					+ "that the interfaces do not describe");

	@ArchTest
	static final ArchRule stagesHoldNoReassignableState = fields()
			.should().beFinal()
			.because("every stage wraps the one protobuf builder the chain started with; a stage that could "
					+ "swap it would drop the fields already set");

	@ArchTest
	static final ArchRule generatedCodeNeedsOnlyProtobuf = classes()
			.should(onlyHaveDependenciesWhere(onTheJdkProtobufOrTheirOwnPackage()))
			.because("the plugin is a build-time tool: a generated class that reached back into it would put "
					+ "the plugin, Maven and Eclipse JDT on every consumer's run-time class path, where "
					+ "protobuf-java and the messages being built are all a builder needs");

	private static DescribedPredicate<Dependency> onTheJdkProtobufOrTheirOwnPackage() {
		DescribedPredicate<JavaClass> protobuf = resideInAnyPackage("java..", "com.google.protobuf..")
				.or(assignableTo(MESSAGE_OR_BUILDER))
				.or(assignableTo(PROTOBUF_ENUM));
		return DescribedPredicate.describe("on the JDK, protobuf-java, the messages they build or their own package",
				dependency -> isWithinItsPackage(dependency) || protobuf.test(dependency.getTargetClass()));
	}

	private static boolean isWithinItsPackage(Dependency dependency) {
		return dependency.getTargetClass().getPackageName().equals(dependency.getOriginClass().getPackageName());
	}
}

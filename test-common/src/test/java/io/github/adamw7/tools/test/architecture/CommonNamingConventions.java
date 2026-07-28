package io.github.adamw7.tools.test.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Naming conventions that hold for every module except {@code
 * claude-code-enforcer}, whose abstract rule base classes ({@code
 * ClaudeCodeEnforcerRule}, {@code MarkdownFormatRule}, {@code JsonFileRule})
 * are named after the maven-enforcer rules that extend them and are public API
 * that poms configure by name. Keeping these rules apart from
 * {@link CommonCodingConventions} makes that exemption a visible choice — one
 * module not importing this library — instead of a rule quietly weakened for
 * everybody.
 */
public class CommonNamingConventions {

	@ArchTest
	static final ArchRule abstractClassesAreNamedWithAbstractPrefix = classes()
			.that().areNotInterfaces()
			.and().areTopLevelClasses()
			.and().haveModifier(JavaModifier.ABSTRACT)
			.should().haveSimpleNameStartingWith("Abstract")
			.because("an Abstract prefix signals at a glance that a type is meant to be extended, not used directly")
			.allowEmptyShould(true);
}

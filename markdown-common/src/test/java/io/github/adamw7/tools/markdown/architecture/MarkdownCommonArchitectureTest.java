package io.github.adamw7.tools.markdown.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.test.architecture.CommonCodingConventions;

/**
 * Architecture rules for the shared Markdown reader. What this module exists for
 * is to be depended on from both sides of a document — the
 * {@code claude-code-enforcer} rule that judges a {@code CLAUDE.md} and the
 * {@code adopt} conformer that reshapes one — so the rules here pin the property
 * that makes that possible: it depends on nothing but the JDK.
 * <p>
 * A dependency added here is inherited by every consumer, including the adopted
 * repositories that resolve the enforcer rule as a maven-enforcer-plugin
 * dependency. Only production classes are analysed.
 */
@AnalyzeClasses(packages = "io.github.adamw7.tools.markdown", importOptions = ImportOption.DoNotIncludeTests.class)
public class MarkdownCommonArchitectureTest {

	@ArchTest
	static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);

	@ArchTest
	static final ArchRule theReaderIsFrameworkFree = noClasses()
			.should().dependOnClassesThat().resideOutsideOfPackages("java..", "javax.annotation..",
					"io.github.adamw7.tools.markdown..")
			.because("both a maven-enforcer rule and a plain adoption library read Markdown through this "
					+ "module; a dependency here would travel to every consumer of either, which is exactly "
					+ "what kept the reader duplicated on both sides in the first place");

	@ArchTest
	static final ArchRule theReaderDoesNotWriteWhereItWasNotAsked = noClasses()
			.that().haveSimpleNameNotEndingWith("MarkdownText")
			.should().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Files")
			.because("MarkdownText owns the file access, so a reader of an already-loaded document cannot "
					+ "quietly reach the disk; every check that reads a project goes through the one class "
					+ "its callers can point at a path")
			.allowEmptyShould(true);
}

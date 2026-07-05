package io.github.adamw7.tools.data.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules for the data module, enforced with ArchUnit so that the
 * package layering stays intact as the code evolves. Only production classes
 * are analysed; test classes are excluded via {@link ImportOption}.
 */
@AnalyzeClasses(packages = DataArchitectureTest.DATA_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class DataArchitectureTest {

	static final String DATA_PACKAGE = "io.github.adamw7.tools.data";

	private static final String INTERFACES_PACKAGE = "..source.interfaces..";
	private static final String INTERNAL_PACKAGE = "..structure.internal..";
	private static final String STRUCTURE_PACKAGE = "..structure..";

	@ArchTest
	static final ArchRule interfacesDoNotDependOnImplementations = noClasses()
			.that().resideInAPackage(INTERFACES_PACKAGE)
			.should().dependOnClassesThat().resideInAnyPackage("..source.db..", "..source.file..")
			.because("data source contracts must not know their concrete database or file implementations");

	@ArchTest
	static final ArchRule internalTypesStayInsideStructure = classes()
			.that().resideInAPackage(INTERNAL_PACKAGE)
			.should().onlyBeAccessed().byAnyPackage(STRUCTURE_PACKAGE)
			.because("structure.internal is an implementation detail of the structure package");

	@ArchTest
	static final ArchRule exceptionsAreNamedConsistently = classes()
			.that().haveSimpleNameEndingWith("Exception")
			.should().beAssignableTo(Exception.class)
			.because("types named *Exception must actually be exceptions");

	@ArchTest
	static final ArchRule packagesAreFreeOfCycles = slices()
			.matching("io.github.adamw7.tools.data.(*)..")
			.should().beFreeOfCycles();
}

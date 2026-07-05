package io.github.adamw7.tools.data.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.apache.logging.log4j.Logger;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

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

	@ArchTest
	static final ArchRule interfacesPackageHoldsOnlyInterfaces = classes()
			.that().resideInAPackage(INTERFACES_PACKAGE)
			.should().beInterfaces()
			.because("source.interfaces is reserved for data source contracts, not implementations");

	@ArchTest
	static final ArchRule dataSourcesImplementTheContract = classes()
			.that().haveSimpleNameEndingWith("DataSource")
			.and().areNotInterfaces()
			.should().beAssignableTo(IterableDataSource.class)
			.because("every concrete data source must honour the IterableDataSource contract");

	@ArchTest
	static final ArchRule loggersAreConstants = fields()
			.that().haveRawType(Logger.class)
			.should().bePrivate()
			.andShould().beStatic()
			.andShould().beFinal()
			.because("loggers are shared, immutable, class-scoped collaborators");

	@ArchTest
	static final ArchRule productionCodeDoesNotUseStandardStreams = noClasses()
			.should().accessField(System.class, "out")
			.orShould().accessField(System.class, "err")
			.because("production code must log through log4j2 instead of the console");
}

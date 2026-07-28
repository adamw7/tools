package io.github.adamw7.tools.data.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;
import io.github.adamw7.tools.test.architecture.CommonCodingConventions;
import io.github.adamw7.tools.test.architecture.CommonNamingConventions;

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
	private static final String FILE_PACKAGE = "..source.file..";
	private static final String DB_PACKAGE = "..source.db..";
	private static final String MCP_PACKAGE = "..uniqueness.mcp..";
	private static final String UNIQUENESS_PACKAGE = "..uniqueness..";
	private static final String NETWORK_PACKAGE = "..network..";
	private static final String CONCURRENT_PACKAGE = "java.util.concurrent..";

	@ArchTest
	static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);

	@ArchTest
	static final ArchTests commonNamingConventions = ArchTests.in(CommonNamingConventions.class);

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
	static final ArchRule jdbcStaysInTheDbPackage = noClasses()
			.that().resideOutsideOfPackage(DB_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage("java.sql..")
			.because("JDBC is an implementation detail of the db data sources; no other package may touch java.sql");

	@ArchTest
	static final ArchRule dataStructuresStayIndependentOfDataSources = noClasses()
			.that().resideInAPackage(STRUCTURE_PACKAGE)
			.should().dependOnClassesThat().resideInAnyPackage("..source..", "..network..", "..compression..")
			.because("the structure package is a reusable collections library and must not couple to data sources");

	@ArchTest
	static final ArchRule dataStructuresDeclareNoSynchronizedMethods = noMethods()
			.that().areDeclaredInClassesThat().resideInAPackage(STRUCTURE_PACKAGE)
			.should().haveModifier(JavaModifier.SYNCHRONIZED)
			.because("the open-addressing structures are documented as not thread-safe; a synchronized "
					+ "method would silently contradict that contract");

	@ArchTest
	static final ArchRule dataStructuresUseNoConcurrencyUtilities = noClasses()
			.that().resideInAPackage(STRUCTURE_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(CONCURRENT_PACKAGE)
			.because("the structures make no thread-safety provisions, so a java.util.concurrent "
					+ "dependency would misleadingly suggest they are safe to share across threads");

	@ArchTest
	static final ArchRule networkSwitchGuardsWithSynchronization = methods()
			.that().haveName("off")
			.and().areDeclaredInClassesThat().resideInAPackage(NETWORK_PACKAGE)
			.should().haveModifier(JavaModifier.SYNCHRONIZED)
			.because("Switch.off() is documented as synchronized so concurrent callers cannot race "
					+ "while installing the blocking proxy selector");

	@ArchTest
	static final ArchRule networkSwitchFlagIsVolatile = fields()
			.that().haveName("isOff")
			.and().areDeclaredInClassesThat().resideInAPackage(NETWORK_PACKAGE)
			.should().haveModifier(JavaModifier.VOLATILE)
			.because("Switch is documented as guarded by a volatile flag so its off state publishes "
					+ "across threads without further locking");

	@ArchTest
	static final ArchRule uniquenessCoreDoesNotDependOnMcpAdapter = noClasses()
			.that().resideInAPackage(UNIQUENESS_PACKAGE)
			.and().resideOutsideOfPackage(MCP_PACKAGE)
			.should().dependOnClassesThat().resideInAPackage(MCP_PACKAGE)
			.because("the MCP adapter is a delivery mechanism on top of the uniqueness core, not a dependency of it");

	@ArchTest
	static final ArchRule sourceLayersDependInOneDirection = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.layer("Contracts").definedBy(INTERFACES_PACKAGE)
			.layer("Compression").definedBy("..compression..")
			.layer("FileSources").definedBy(FILE_PACKAGE)
			.layer("DbSources").definedBy(DB_PACKAGE)
			.whereLayer("FileSources").mayOnlyAccessLayers("Contracts", "Compression")
			.whereLayer("DbSources").mayOnlyAccessLayers("Contracts")
			.whereLayer("Compression").mayNotAccessAnyLayer()
			.as("the source layers must depend only downwards: concrete sources on their "
					+ "contracts (and, for files, on compression), never on each other");
}

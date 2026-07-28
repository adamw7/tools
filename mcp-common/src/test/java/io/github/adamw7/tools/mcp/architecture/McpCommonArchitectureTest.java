package io.github.adamw7.tools.mcp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.mcp.McpTool;
import io.github.adamw7.tools.test.architecture.CommonCodingConventions;
import io.github.adamw7.tools.test.architecture.CommonNamingConventions;

/**
 * Architecture rules for the shared MCP scaffolding module. They keep the
 * reusable transport wiring and tool SPI honest as the code evolves: the tool
 * contract stays an interface, logging goes through log4j2, and the module
 * never terminates or writes to the console of the server that embeds it. Only
 * production classes are analysed; test classes are excluded via
 * {@link ImportOption}.
 */
@AnalyzeClasses(packages = McpCommonArchitectureTest.MCP_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
public class McpCommonArchitectureTest {

	static final String MCP_PACKAGE = "io.github.adamw7.tools.mcp";

	@ArchTest
	static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);

	@ArchTest
	static final ArchTests commonNamingConventions = ArchTests.in(CommonNamingConventions.class);

	@ArchTest
	static final ArchRule toolSpiIsAContract = classes()
			.that().haveSimpleName("McpTool")
			.should().beInterfaces()
			.because("McpTool is the tool SPI that concrete servers implement, not a concrete tool");

	@ArchTest
	static final ArchRule toolsImplementTheContract = classes()
			.that().haveSimpleNameEndingWith("Tool")
			.and().areNotInterfaces()
			.should().beAssignableTo(McpTool.class)
			.because("every concrete *Tool must honour the McpTool contract")
			.allowEmptyShould(true);
}

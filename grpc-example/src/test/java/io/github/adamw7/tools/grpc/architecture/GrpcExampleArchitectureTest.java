package io.github.adamw7.tools.grpc.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.github.adamw7.tools.test.architecture.CommonCodingConventions;
import io.github.adamw7.tools.test.architecture.CommonNamingConventions;

/**
 * Architecture rules for the gRPC example. The module has no hand-written main
 * code: the example lives in {@code src/test} so it can use the builders the
 * plugin generates into {@code generated-test-sources}, which is why the
 * analysis imports {@link ImportOption.OnlyIncludeTests}. The hand-written
 * classes are held to the production conventions all the same, since the
 * client and server are what a reader copies.
 * <p>
 * The generated builders are left out here and held to their own contract by
 * {@link GeneratedBuildersArchitectureTest}; protoc's message and stub classes
 * are main classes, so the test-only import leaves them out already.
 */
@AnalyzeClasses(packages = GrpcExampleArchitectureTest.EXAMPLE_PACKAGE,
		importOptions = { ImportOption.OnlyIncludeTests.class, GrpcExampleArchitectureTest.WithoutGeneratedBuilders.class })
public class GrpcExampleArchitectureTest {

	static final String EXAMPLE_PACKAGE = "io.github.adamw7.tools.grpc";

	/** Leaves out the builders protogen generates, which follow a contract of their own. */
	public static final class WithoutGeneratedBuilders implements ImportOption {

		private static final String BUILDERS_PACKAGE = "/io/github/adamw7/tools/grpc/builders/";

		@Override
		public boolean includes(Location location) {
			return !location.contains(BUILDERS_PACKAGE);
		}
	}

	private static final String PROTOBUF_BUILDER = "com.google.protobuf.MessageLite$Builder";
	private static final String GREETER_SERVER = EXAMPLE_PACKAGE + ".GreeterServer";
	private static final String GREETER_CLIENT = EXAMPLE_PACKAGE + ".GreeterClient";

	@ArchTest
	static final ArchTests commonCodingConventions = ArchTests.in(CommonCodingConventions.class);

	@ArchTest
	static final ArchTests commonNamingConventions = ArchTests.in(CommonNamingConventions.class);

	@ArchTest
	static final ArchRule messagesAreBuiltThroughTheGeneratedBuilders = noClasses()
			.should().dependOnClassesThat().areAssignableTo(PROTOBUF_BUILDER)
			.because("the module exists to show protogen's builders in a working service; one message built "
					+ "with protoc's own newBuilder() would leave its required fields to a run-time check, "
					+ "which is the gap the generated chain closes at compile time");

	@ArchTest
	static final ArchRule unitTestsStayInProcess = noClasses()
			.that().haveSimpleNameEndingWith("Test")
			.should().dependOnClassesThat().haveFullyQualifiedName(GREETER_SERVER)
			.orShould().dependOnClassesThat().haveFullyQualifiedName(GREETER_CLIENT)
			.orShould().dependOnClassesThat().haveFullyQualifiedName("io.grpc.Grpc")
			.orShould().dependOnClassesThat().resideInAPackage("io.grpc.netty..")
			.because("GreeterServer and GreeterClient bind and dial the fixed port 50051 over Netty, which two "
					+ "builds on one machine contend for; the in-process transport exercises the same service "
					+ "and builders without opening a socket");
}

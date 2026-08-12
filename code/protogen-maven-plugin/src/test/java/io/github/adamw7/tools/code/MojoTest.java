package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

// Every test here runs the Mojo end to end: it generates builders and compiles
// the result with the JDK compiler, which can exceed the global 1-second
// per-test timeout on a cold or loaded CI runner. Grant a generous bound that
// still catches a genuine hang in code generation or compilation.
@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class MojoTest {
	
	private static final Logger log = LogManager.getLogger(MojoTest.class.getName());

	private static final String PROTO2_PKG = "io.github.adamw7.tools.code.protos";
	private static final String PROTO2_OUTPUT = "com.sth.generated";
	private static final String PROTO3_PKG = "io.github.adamw7.tools.code.proto3";
	private static final String PROTO3_OUTPUT = "com.sth.generated.proto3";

	protected static String GENERATED_SOURCES;
	protected static String TARGET;

	protected final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

	@BeforeAll
	public static void setupPath() {
		TARGET = new File(MojoTest.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
		GENERATED_SOURCES = TARGET + "/generated-sources";
	}

	@Test
	public void happyPath() {
		CodeMojo mojo = mojoFor(PROTO2_PKG, PROTO2_OUTPUT);

		mojo.execute();
		File dir = new File(GENERATED_SOURCES);
		Set<String> dirSet = new HashSet<>(Arrays.asList(dir.list()));
		assertTrue(dirSet.contains("com"));

		compileSources(GENERATED_SOURCES + File.separator + mojo.outputpackage.replace(".", File.separator));
	}

	/**
	 * Address has two required fields and nothing else, so the whole chain the
	 * plugin exists to generate fits in one assertion set: the builder starts at
	 * the first required field, each setter hands back the interface for the next
	 * one, and only the last of them exposes the optional interface that declares
	 * {@code build()}. Generating fewer classes than this, or wiring one setter to
	 * the wrong interface, is what would let a caller build a message with a
	 * required field missing — the failure the plugin shifts to compile time.
	 */
	@Test
	public void requiredSettersChainThroughEveryRequiredFieldBeforeBuild() throws IOException {
		mojoFor(PROTO2_PKG, PROTO2_OUTPUT).execute();

		assertDeclares("AddressBuilder", "public class AddressBuilder implements AddressStreetIfc {");
		assertDeclares("AddressStreetIfc", "AddressNumberIfc setStreet(String street);");
		assertDeclares("AddressNumberIfc", "AddressOptionalIfc setNumber(int number);");
		assertDeclares("AddressOptionalIfc", "Address build();");
	}

	/**
	 * The chain above only holds if every link is written out. A generator that
	 * emitted the builder alone would still compile, so the file set is asserted
	 * as a whole rather than left to the compiler.
	 */
	@Test
	public void generatesAnInterfaceAndAnImplementationForEveryRequiredField() throws IOException {
		mojoFor(PROTO2_PKG, PROTO2_OUTPUT).execute();

		assertEquals(
				List.of("AddressBuilder.java", "AddressNumberIfc.java", "AddressNumberImpl.java",
						"AddressOptionalIfc.java", "AddressOptionalImpl.java", "AddressStreetIfc.java"),
				generatedFilesNamed("Address"));
	}

	@Test
	public void generatesOneofAccessorsOnTheOptionalInterface() throws IOException {
		mojoFor(PROTO2_PKG, PROTO2_OUTPUT).execute();

		assertDeclares("SampleMessageOptionalIfc", "SampleMessage.TestOneofCase getTestOneofCase();");
		assertDeclares("SampleMessageOptionalIfc", "SampleMessageOptionalIfc clearTestOneof();");
	}

	@Test
	public void proto3GeneratesAndCompiles() {
		CodeMojo mojo = mojoFor(PROTO3_PKG, PROTO3_OUTPUT);

		mojo.execute();
		File dir = new File(GENERATED_SOURCES);
		Set<String> dirSet = new HashSet<>(Arrays.asList(dir.list()));
		assertTrue(dirSet.contains("com"));

		compileSources(GENERATED_SOURCES + File.separator + mojo.outputpackage.replace(".", File.separator));
	}

	/**
	 * proto3 has no {@code required}, so the chain is built from the fields that
	 * carry no presence — the map and repeated ones — and those are exactly the
	 * fields that must not get a has-accessor.
	 */
	@Test
	public void proto3ChainsPresencelessFieldsAndDeclaresNoHasAccessorForThem() throws IOException {
		mojoFor(PROTO3_PKG, PROTO3_OUTPUT).execute();

		assertDeclares(PROTO3_OUTPUT, "Proto3SampleBuilder",
				"public class Proto3SampleBuilder implements Proto3SampleScoresIfc {");
		assertDeclares(PROTO3_OUTPUT, "Proto3SampleRolesIfc",
				"Proto3SampleOptionalIfc setRoles(java.util.List<String> roles);");
		assertFalse(asOneLine(generatedSource(PROTO3_OUTPUT, "Proto3SampleRolesIfc")).contains("hasRoles()"),
				"a repeated field has no presence, so no has-accessor may be declared for it");
	}

	@Test
	public void restoresContextClassLoader() {
		CodeMojo mojo = mojoFor(PROTO2_PKG, PROTO2_OUTPUT);

		ClassLoader beforeExecution = Thread.currentThread().getContextClassLoader();
		mojo.execute();

		assertSame(beforeExecution, Thread.currentThread().getContextClassLoader(),
				"The goal is declared thread-safe, so it must not leave its classpath on the build thread");
	}

	@Test
	public void restoresContextClassLoaderWhenGenerationFails(@TempDir Path tempDir) throws IOException {
		// A regular file where the output directory should go makes generation blow up
		// after the classloader has already been swapped in.
		Path blockingFile = Files.createFile(tempDir.resolve("not-a-directory"));
		CodeMojo mojo = mojoFor(PROTO2_PKG, PROTO2_OUTPUT);
		mojo.generatedSourcesDir = blockingFile.toString();

		ClassLoader beforeExecution = Thread.currentThread().getContextClassLoader();
		assertThrows(UncheckedIOException.class, mojo::execute);

		assertSame(beforeExecution, Thread.currentThread().getContextClassLoader(),
				"A failing execution must restore the build thread's classloader too");
	}

	/**
	 * The goal scans for messages through the thread context classloader, so the
	 * runtime classpath has to be on it before the scan or a project's own
	 * generated messages are invisible. The extension is undone before
	 * {@link CodeMojo#execute()} returns, which leaves calling it directly as the
	 * only place the result can be observed.
	 */
	@Test
	public void extendsTheContextClassPathWithTheRuntimeElements(@TempDir Path classpathElement) {
		CodeMojo mojo = mojoFor(PROTO2_PKG, PROTO2_OUTPUT);
		// A null element is what a Maven project with an unresolved artifact hands over.
		mojo.runtimeClasspathElements = Arrays.asList(classpathElement.toString(), null);

		ClassLoader beforeExtension = Thread.currentThread().getContextClassLoader();
		try {
			mojo.extendClassPath();

			ClassLoader extended = Thread.currentThread().getContextClassLoader();
			assertNotSame(beforeExtension, extended, "the scan must run through the extended classpath");
			URL[] urls = assertInstanceOf(URLClassLoader.class, extended).getURLs();
			assertEquals(1, urls.length, "the null element must be skipped rather than turned into a URL");
			assertTrue(urls[0].getPath().contains(classpathElement.getFileName().toString()),
					"the runtime classpath element must be on the classloader; was: " + urls[0]);
		} finally {
			Thread.currentThread().setContextClassLoader(beforeExtension);
		}
	}

	private CodeMojo mojoFor(String inputPkg, String outputPkg) {
		CodeMojo mojo = new CodeMojo();
		mojo.generatedSourcesDir = GENERATED_SOURCES;
		mojo.pkgs = new String[] { inputPkg };
		mojo.outputpackage = outputPkg;
		mojo.runtimeClasspathElements = List.of();
		return mojo;
	}

	private void assertDeclares(String simpleName, String declaration) throws IOException {
		assertDeclares(PROTO2_OUTPUT, simpleName, declaration);
	}

	private void assertDeclares(String outputPkg, String simpleName, String declaration) throws IOException {
		String source = generatedSource(outputPkg, simpleName);
		assertTrue(asOneLine(source).contains(declaration),
				simpleName + " does not declare: " + declaration + System.lineSeparator() + source);
	}

	private String generatedSource(String outputPkg, String simpleName) throws IOException {
		return Files.readString(outputDir(outputPkg).resolve(simpleName + ".java"));
	}

	/** The generated file names starting with the message's name, sorted so a failure reads the same way twice. */
	private List<String> generatedFilesNamed(String messageName) throws IOException {
		try (Stream<Path> entries = Files.list(outputDir(PROTO2_OUTPUT))) {
			return entries.map(path -> path.getFileName().toString())
					.filter(name -> name.startsWith(messageName)).sorted().toList();
		}
	}

	private static Path outputDir(String outputPkg) {
		return Path.of(GENERATED_SOURCES, outputPkg.split("\\."));
	}

	/** Collapses the formatter's line breaks and indentation, so an assertion reads as one line of Java. */
	private static String asOneLine(String code) {
		return code.replaceAll("\\s+", " ").strip();
	}

	private void compileSources(String dir) {
		List<String> generatedSources = getAllGeneratedSources(dir);
		compileFiles(generatedSources);
	}

	private List<String> getAllGeneratedSources(String dir) {
		File dirFile = new File(dir);
		return Arrays.stream(Objects.requireNonNull(dirFile.list((inputDir, name) -> name.endsWith(".java")))).map(s -> dir + File.separator + s).toList();
	}

	static class JavaSourceFromFile extends SimpleJavaFileObject {
		final String file;

		JavaSourceFromFile(String file) {
			super(new File(file).toURI(), Kind.SOURCE);
			this.file = file;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			try {
				return Files.readString(Paths.get(file));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	private void compileFiles(List<String> generatedSourceFiles) {
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		List<JavaFileObject> compilationUnits = generatedSourceFiles.stream()
				.<JavaFileObject>map(JavaSourceFromFile::new).toList();
		CompilationTask task = compiler.getTask(null, createFileManager(), diagnostics, null, null, compilationUnits);

		boolean success = task.call();
		if (!success) {
			logCompilerError(diagnostics);
		} else {
			log.info("Compiled {} generated sources", compilationUnits.size());
		}
		assertTrue(success);
	}

	private StandardJavaFileManager createFileManager() {
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		try {
			fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(new File(TARGET + "/test-classes/")));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return fileManager;
	}

	private void logCompilerError(DiagnosticCollector<JavaFileObject> diagnostics) {
		for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
			log.error(diagnostic.getCode());
			log.error(diagnostic.getKind());
			log.error(diagnostic.getPosition());
			log.error(diagnostic.getStartPosition());
			log.error(diagnostic.getEndPosition());
			log.error(diagnostic.getSource());
			log.error(diagnostic.getMessage(null));
		}
	}

	@AfterEach
	public void cleanUp() throws IOException {
		deleteRecursively(Path.of(GENERATED_SOURCES));
	}

	private void deleteRecursively(Path path) throws IOException {
		if (Files.notExists(path)) {
			return;
		}
		if (Files.isDirectory(path)) {
			try (var children = Files.newDirectoryStream(path)) {
				for (Path child : children) {
					deleteRecursively(child);
				}
			}
		}
		Files.delete(path);
	}

}

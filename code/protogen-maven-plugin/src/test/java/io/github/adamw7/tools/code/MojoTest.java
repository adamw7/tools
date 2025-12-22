package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MojoTest {
	
	private final static Logger log = LogManager.getLogger(MojoTest.class.getName());

	protected static String GENERATED_SOURCES;
	protected static String TARGET;

	protected final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

	@BeforeAll
	public static void setupPath() {
		TARGET = new File(MojoTest.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
		GENERATED_SOURCES = TARGET + "/generated-sources/";
	}

	@Test
	public void happyPath() {
		CodeMojo mojo = new CodeMojo();
		mojo.generatedSourcesDir = GENERATED_SOURCES;
		mojo.pkgs = new String[] { "io.github.adamw7.tools.code.protos" };
		mojo.outputpackage = "com.sth.generated";
		mojo.project = new MavenProject();

		mojo.execute();
		File dir = new File(GENERATED_SOURCES);
		Set<String> dirSet = new HashSet<>(Arrays.asList(dir.list()));
		assertTrue(dirSet.contains("com"));

		compileSources(GENERATED_SOURCES + File.separator + mojo.outputpackage.replace(".", File.separator));
	}

	private void compileSources(String dir) {
		List<String> optionalIfcs = getAllOptionalIfcs(dir);		
		compileFiles(optionalIfcs);		
	}

	private void compileFiles(List<String> generatedSourceFiles) {
		for (String generatedSourceFile : generatedSourceFiles) {
			compileFile(generatedSourceFile);
		}
	}

	private List<String> getAllOptionalIfcs(String dir) {
		File dirFile = new File(dir);
		return Arrays.stream(Objects.requireNonNull(dirFile.list((inputDir, name) -> name.contains("OptionalIfc.java")))).map(s -> dir + File.separator + s).toList();
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
				return new String(Files.readAllBytes(Paths.get(file)));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	private void compileFile(String fileName) {
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		JavaFileObject file = new JavaSourceFromFile(fileName);

		Iterable<? extends JavaFileObject> compilationUnits = List.of(file);
		CompilationTask task = compiler.getTask(null, createFileManager(), diagnostics, null, null, compilationUnits);

		boolean success = task.call();
		if (!success) {
			logCompilerError(diagnostics);			
		} else {
			log.info("Compiled {}", file);
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
	public void cleanUp() {
		File dir = new File(GENERATED_SOURCES);
		dir.delete();
	}

}

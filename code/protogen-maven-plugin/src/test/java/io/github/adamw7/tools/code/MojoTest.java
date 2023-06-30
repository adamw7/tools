package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MojoTest {

	protected static String GENERATED_SOURCES;
	
	@BeforeAll
	public static void setupPath() {
		String target = new File(MojoTest.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
		GENERATED_SOURCES = target + "/generated-sources/";
	}
	
	@Test
	public void happyPath() {
		CodeMojo mojo = new CodeMojo();
		mojo.generatedSourcesDir = GENERATED_SOURCES;
		mojo.pkgs = new String[]{"io.github.adamw7.tools.code.protos"};
		mojo.outputpackage = "com.sth.generated";
		mojo.project = new MavenProject();
		
		mojo.execute();
		File dir = new File(GENERATED_SOURCES);
		Set<String> dirSet = new HashSet<>(Arrays.asList(dir.list()));
		assertTrue(dirSet.contains("com"));
	}
	
	@AfterEach
	public void cleanUp() {
		File dir = new File(GENERATED_SOURCES);
		dir.delete();
	}
	
}

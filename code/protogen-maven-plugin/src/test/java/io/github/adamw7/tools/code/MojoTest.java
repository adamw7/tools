package io.github.adamw7.tools.code;

import java.io.File;
import java.io.IOException;

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
		mojo.execute();
	}
	
	@AfterEach
	public void cleanUp() throws IOException {
		File dir = new File(GENERATED_SOURCES);
		dir.delete();
	}
	
}

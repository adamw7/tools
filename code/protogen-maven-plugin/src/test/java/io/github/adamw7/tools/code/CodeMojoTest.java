package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class CodeMojoTest {

	@Test
	public void happyPath() {
		new CodeMojo().execute();
		File outputDir = new File("target/generated-sources/io/github/adamw7/tools/code/");
		assertTrue(outputDir.exists());
		File generatedClassFile = new File(outputDir.getAbsolutePath() + "/PersonBuilder.java"); 
		assertTrue(generatedClassFile.exists());
	}
}

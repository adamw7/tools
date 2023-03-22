package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

public class CodeGenerationTest {

	@Test
	public void sourcesExist() {
		File outputDir = new File("target/generated-sources/io/github/adamw7/tools/code/");
		assertTrue(outputDir.exists());
		File generatedClassFile = new File(outputDir.getAbsolutePath() + "/PersonBuilder.java"); 
		assertTrue(generatedClassFile.exists());
	}
	
	@Test
	public void compiledExist() {
		File outputDir = new File("target/classes/io/github/adamw7/tools/code/");
		assertTrue(outputDir.exists());
		File generatedClassFile = new File(outputDir.getAbsolutePath() + "/PersonBuilder.class"); 
		assertTrue(generatedClassFile.exists());
	}
}

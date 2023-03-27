package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.code.protos.Person;

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
	
	@Test
	public void codeExecutes() {
		PersonBuilder builder = new PersonBuilder();
		Person person = builder.setId(1).setDepartment("dep").setEmail("sth@sth.net").setName("Adam").
				setSalary(1000L).setFactor(0f).setGender(1).setPhone(12345678L).setLevel(6).setGrade(10L).build();
		
		assertTrue(builder.hasId());
		
		assertTrue(person.hasId());
		assertTrue(person.hasDepartment());
		assertTrue(person.hasEmail());
		assertTrue(person.hasName());
		assertTrue(person.hasSalary());
		assertTrue(person.hasFactor());
		assertTrue(person.hasGender());	
		assertTrue(person.hasPhone());		
		assertTrue(person.hasLevel());
		assertTrue(person.hasGrade());		
		
		assertEquals(1, person.getId());
		assertEquals("dep", person.getDepartment());
		assertEquals("sth@sth.net", person.getEmail());
		assertEquals("Adam", person.getName());
		assertEquals(1000L, person.getSalary());
		assertEquals(0f, person.getFactor());
		assertEquals(1, person.getGender());
		assertEquals(12345678L, person.getPhone());
		assertEquals(6, person.getLevel());
		assertEquals(10L, person.getGrade());
	}
}

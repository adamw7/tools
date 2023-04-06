package io.github.adamw7.tools.code;

import java.io.File;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.code.protos.Person;

import static org.junit.jupiter.api.Assertions.*;

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
		assertFalse(builder.hasId());
		DepartmentIfc department = builder.setId(1);
		assertTrue(builder.hasId());
		assertFalse(department.hasDepartment());
		PersonOptionalIfc optional = department.setDepartment("dep");
		assertTrue(department.hasDepartment());
		Person person = optional.setEmail("sth@sth.net").setName("Adam").
				setSalary(1000L).setFactor(0f).setGender(1).setPhone(12345678L).setLevel(6).
				setGrade(10L).setUnit(30).setExternalId(500L).setActive(true).setLocation(17).
				setCooridantes(9999999L).setPercent(0.4).build();

		assertOptional(optional);

		assertTrue(builder.hasId());

		assertPersonFieldsAreSet(person);

		assertPersonFieldsHaveProperValues(person);
	}

	private static void assertPersonFieldsHaveProperValues(Person person) {
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
		assertEquals(30, person.getUnit());
		assertEquals(500L, person.getExternalId());
		assertEquals(true, person.getActive());
		assertEquals(17, person.getLocation());
		assertEquals(9999999L, person.getCooridantes());
		assertEquals(0.4, person.getPercent());
	}

	private static void assertPersonFieldsAreSet(Person person) {
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
		assertTrue(person.hasUnit());
		assertTrue(person.hasExternalId());
		assertTrue(person.hasActive());
		assertTrue(person.hasLocation());
		assertTrue(person.hasCooridantes());
		assertTrue(person.hasPercent());
	}

	private static void assertOptional(PersonOptionalIfc optional) {
		assertTrue(optional.hasEmail());
		assertTrue(optional.hasName());
		assertTrue(optional.hasSalary());
		assertTrue(optional.hasFactor());
		assertTrue(optional.hasGender());
		assertTrue(optional.hasPhone());
		assertTrue(optional.hasLevel());
		assertTrue(optional.hasGrade());
		assertTrue(optional.hasUnit());
		assertTrue(optional.hasExternalId());
		assertTrue(optional.hasActive());
		assertTrue(optional.hasLocation());
		assertTrue(optional.hasCooridantes());
		assertTrue(optional.hasPercent());
	}
}

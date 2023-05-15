package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;

import io.github.adamw7.tools.code.protos.Address;
import io.github.adamw7.tools.code.protos.City;
import io.github.adamw7.tools.code.protos.Person;
import io.github.adamw7.tools.code.protos.Person.CLASSIFICATION;

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
		MappingIfc mapping = department.setDepartment("dep");
		assertTrue(department.hasDepartment());
		Map<String, Integer> map = new HashMap<>();
		map.put("S", 1);
		List<Integer> ids = new ArrayList<>();
		ids.add(55);
		PersonOptionalIfc optional = mapping.setMapping(map).setIds(ids);		
		Person person = optional.setEmail("sth@sth.net").setName("Adam").
				setSalary(1000L).setFactor(0f).setGender(1).setPhone(12345678L).setLevel(6).
				setGrade(10L).setUnit(30).setExternalId(500L).setActive(true).setLocation(17).
				setCooridantes(9999999L).setPercent(0.4).setClassification(CLASSIFICATION.business).build();

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
		assertEquals(CLASSIFICATION.business, person.getClassification());	
		assertEquals(1, person.getMappingCount());
		assertEquals(55, person.getIds(0));
		assertEquals(1, person.getIdsCount());
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
		assertTrue(person.hasClassification());		
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
		assertTrue(optional.hasClassification());
	}
	
	@Test
	public void defaultValues() {
		assertEquals(10, Address.newBuilder().getNumber());
		assertEquals("", Address.newBuilder().getStreet());		
	}
	
	@Test
	public void byteString() {
		City city = City.newBuilder().setDescription(ByteString.copyFrom("desc".getBytes())).build();
		assertTrue(city.hasDescription());
		assertEquals("desc", new String(city.getDescription().toByteArray()));
	}
}

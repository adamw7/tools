package io.github.adamw7.tools.code.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.protobuf.Descriptors.FieldDescriptor;

import io.github.adamw7.tools.code.protos.Foo;
import io.github.adamw7.tools.code.protos.Person;

public class ClassInfoTest {

	private final ClassInfo personInfo = new ClassInfo(Person.getDescriptor(), "in.pkg", "out.pkg");

	private List<String> names(List<FieldDescriptor> fields) {
		return fields.stream().map(FieldDescriptor::getName).toList();
	}

	@Test
	public void exposesMessageName() {
		assertEquals("Person", personInfo.name());
	}

	@Test
	public void exposesFullName() {
		assertEquals("Person", personInfo.fullName());
	}

	@Test
	public void retainsInputAndOutputPackages() {
		assertEquals("in.pkg", personInfo.getInputPkg());
		assertEquals("out.pkg", personInfo.getOutputPkg());
	}

	@Test
	public void collectsRequiredFields() {
		List<String> required = names(personInfo.required());
		assertEquals(2, required.size());
		assertTrue(required.contains("id"));
		assertTrue(required.contains("department"));
	}

	@Test
	public void collectsOptionalFields() {
		List<String> optional = names(personInfo.optional());
		assertTrue(optional.contains("name"));
		assertTrue(optional.contains("email"));
		assertFalse(optional.contains("id"));
	}

	@Test
	public void collectsMapFields() {
		List<String> maps = names(personInfo.map());
		assertEquals(List.of("mapping"), maps);
	}

	@Test
	public void collectsRepeatedFieldsExcludingMaps() {
		List<String> repeated = names(personInfo.repeated());
		assertEquals(3, repeated.size());
		assertTrue(repeated.contains("ids"));
		assertTrue(repeated.contains("classifications"));
		assertTrue(repeated.contains("friends"));
	}

	@Test
	public void collectsGroupFields() {
		assertEquals(1, personInfo.getGroupFields().size());
	}

	@Test
	public void nonOptionalUnionsRequiredMapAndRepeated() {
		assertEquals(6, personInfo.nonOptional().size());
	}

	@Test
	public void personHasNoPureComplexFields() {
		assertTrue(personInfo.getPureComplexFields().isEmpty());
	}

	@Test
	public void detectsSingularMessageAsPureComplexField() {
		ClassInfo bazInfo = new ClassInfo(Foo.Baz.getDescriptor(), "in.pkg", "out.pkg");

		List<String> pureComplex = names(bazInfo.getPureComplexFields());
		assertEquals(List.of("a"), pureComplex);
	}
}

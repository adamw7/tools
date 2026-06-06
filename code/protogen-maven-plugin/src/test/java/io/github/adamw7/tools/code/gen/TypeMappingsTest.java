package io.github.adamw7.tools.code.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessage;

import io.github.adamw7.tools.code.protos.City;
import io.github.adamw7.tools.code.protos.Person;

public class TypeMappingsTest {

	private TypeMappings typeMappings;

	@BeforeEach
	public void setUp() {
		Set<Class<? extends GeneratedMessage>> messages = new HashSet<>();
		messages.add(Person.class);
		typeMappings = new TypeMappings(messages);
	}

	private String mapFor(Descriptor descriptor, String fieldName) {
		FieldDescriptor field = descriptor.findFieldByName(fieldName);
		return typeMappings.get(field);
	}

	@Test
	public void mapsStringScalar() {
		assertEquals("String", mapFor(Person.getDescriptor(), "name"));
	}

	@Test
	public void mapsIntScalar() {
		assertEquals("int", mapFor(Person.getDescriptor(), "id"));
	}

	@Test
	public void mapsLongScalar() {
		assertEquals("long", mapFor(Person.getDescriptor(), "salary"));
	}

	@Test
	public void mapsFloatScalar() {
		assertEquals("float", mapFor(Person.getDescriptor(), "factor"));
	}

	@Test
	public void mapsBooleanScalar() {
		assertEquals("boolean", mapFor(Person.getDescriptor(), "active"));
	}

	@Test
	public void mapsDoubleScalar() {
		assertEquals("double", mapFor(Person.getDescriptor(), "percent"));
	}

	@Test
	public void mapsBytesToByteString() {
		assertEquals("com.google.protobuf.ByteString", mapFor(City.getDescriptor(), "description"));
	}

	@Test
	public void mapsRepeatedScalarToListOfWrapperType() {
		assertEquals("java.util.List<Integer>", mapFor(Person.getDescriptor(), "ids"));
	}

	@Test
	public void mapsRepeatedMessageToListOfMessage() {
		assertEquals("java.util.List<Person>", mapFor(Person.getDescriptor(), "friends"));
	}

	@Test
	public void mapsMapFieldWrappingPrimitiveValue() {
		assertEquals("java.util.Map<String,Integer>", mapFor(Person.getDescriptor(), "mapping"));
	}

	@Test
	public void mapsEnumToNestedEnumName() {
		assertEquals("Person.CLASSIFICATION", mapFor(Person.getDescriptor(), "classification"));
	}
}

package io.github.adamw7.tools.code.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class UtilsTest {

	@Test
	public void toUpperCamelCase() {
		String camelCase = Utils.toUpperCamelCase("external_id");
		assertEquals("ExternalId", camelCase);

		String multiCamelCase = Utils.toUpperCamelCase("external_id_by_name");
		assertEquals("ExternalIdByName", multiCamelCase);
	}

	@Test
	public void firstToLower() {
		String firstToLower = Utils.firstToLower("Builder");
		assertEquals("builder", firstToLower);

		String shouldNotChange = Utils.firstToLower("builder");
		assertEquals("builder", shouldNotChange);
	}

	@Test
	public void suffix() {
		String doubleSuffix = Utils.getSuffixOf("pkg.EnumType.field", 2, ".");
		assertEquals("EnumType.field", doubleSuffix);

		String singleSuffix = Utils.getSuffixOf("pkg,EnumType,field", 1, ",");
		assertEquals("field", singleSuffix);
	}

	@Test
	public void testGetClassName() {
		String fullName1 = "com.sth.test.Grouping.Group";
		String className1 = Utils.getClassName(fullName1);
		assertEquals("Grouping.Group", className1);

		String fullName2 = "com.example.SomeClass";
		String className2 = Utils.getClassName(fullName2);
		assertEquals("SomeClass", className2);

		String fullName3 = "org.example.MyPackage.MyClass";
		String className3 = Utils.getClassName(fullName3);
		assertEquals("MyPackage.MyClass", className3);

		String fullName4 = "com.abc.XYZ";
		String className4 = Utils.getClassName(fullName4);
		assertEquals("XYZ", className4);

		String fullName5 = "java.lang.String";
		String className5 = Utils.getClassName(fullName5);
		assertEquals("String", className5);

		String fullName6 = "SingleToken";
		String className6 = Utils.getClassName(fullName6);
		assertEquals("SingleToken", className6);
		
		String fullName7 = "";
		String className7 = Utils.getClassName(fullName7);
		assertEquals("", className7);
	}

	@Test
	public void testGetNext() {
		FieldDescriptor desc1 = Mockito.mock(FieldDescriptor.class);
		FieldDescriptor desc2 = Mockito.mock(FieldDescriptor.class);
		FieldDescriptor desc3 = Mockito.mock(FieldDescriptor.class);

		String className = "TestClass";
		List<FieldDescriptor> fields = List.of(desc1, desc2, desc3);

		String result = Utils.getNextImpl(className, fields, Mockito.mock(FieldDescriptor.class));
		assertEquals("TestClassOptionalImpl", result);

		result = Utils.getNextIfc(className, fields, Mockito.mock(FieldDescriptor.class));
		assertEquals("TestClassOptionalIfc", result);
	}

}

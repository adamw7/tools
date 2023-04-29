package io.github.adamw7.tools.code.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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

}

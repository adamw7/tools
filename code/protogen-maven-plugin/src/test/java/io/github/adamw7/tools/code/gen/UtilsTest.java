package io.github.adamw7.tools.code.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class UtilsTest {

	@Test
	public void toUpperCamelCase() {
		String camelCase = Utils.toUpperCamelCase("external_id");
		assertEquals("ExternalId", camelCase);
	}
	
	@Test
	public void firstToLower() {
		String firstToLower = Utils.firstToLower("Builder");
		assertEquals("builder", firstToLower);
	}
	
	@Test
	public void suffix() {
		String suffix = Utils.getSuffixOf("pkg.EnumType.field", 2, ".");
		assertEquals("EnumType.field", suffix);
	}

}

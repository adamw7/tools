package io.github.adamw7.tools.code.gen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

}

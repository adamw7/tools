package io.github.adamw7.tools.code.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * The generated identifiers must not depend on the machine the build runs on.
 * Runs alone under the class-parallel unit-test run: it changes the JVM's default
 * locale, which every concurrently running test would otherwise read.
 */
@Isolated
class UtilsLocaleTest {

	private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

	private Locale saved;

	@BeforeEach
	void switchToTurkish() {
		saved = Locale.getDefault();
		Locale.setDefault(TURKISH);
	}

	@AfterEach
	void restoreLocale() {
		Locale.setDefault(saved);
	}

	@Test
	void upperCasesAnIdentifierTheSameWayUnderATurkishLocale() {
		assertEquals("Id", Utils.firstToUpper("id"));
		assertEquals("IdentityId", Utils.toUpperCamelCase("identity_id"));
	}

	@Test
	void lowerCasesAnIdentifierTheSameWayUnderATurkishLocale() {
		assertEquals("item", Utils.firstToLower("Item"));
		assertEquals("Items", Utils.toUpperCamelCase("ITEMS"));
	}
}

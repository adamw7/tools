package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MainTest {

	@Test
	void rejectsMissingArguments() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> Main.main(new String[0]));
		assertTrue(exception.getMessage().contains("Usage"), exception.getMessage());
	}

	@Test
	void rejectsNullArguments() {
		assertThrows(IllegalArgumentException.class, () -> Main.main(null));
	}

	@Test
	void rejectsBlankRepositoryUrl() {
		assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] { "   " }));
	}
}

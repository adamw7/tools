package io.github.adamw7.tools.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;

public class SampleAppTest {

	@Test
	public void rejectsNullArguments() {
		assertThrows(IllegalArgumentException.class, () -> SampleApp.main(null));
	}

	@Test
	public void rejectsTooFewArguments() {
		assertThrows(IllegalArgumentException.class, () -> SampleApp.main(new String[] { "onlyFile.csv" }));
	}

	@Test
	public void wrapsMissingFileInUncheckedIOException() {
		String[] args = { "doesNotExist.csv", "year1" };
		assertThrows(UncheckedIOException.class, () -> SampleApp.main(args));
	}

	@Test
	public void runsForNonUniqueColumn() {
		String[] args = { Utils.getHouseholdFile(), "year1" };
		assertDoesNotThrow(() -> SampleApp.main(args));
	}

	@Test
	public void runsForUniqueColumn() {
		String[] args = { Utils.getHouseholdFile(), "income" };
		assertDoesNotThrow(() -> SampleApp.main(args));
	}
}

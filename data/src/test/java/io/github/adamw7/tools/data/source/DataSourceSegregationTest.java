package io.github.adamw7.tools.data.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.source.db.InMemorySQLDataSource;
import io.github.adamw7.tools.data.source.db.IterableSQLDataSource;
import io.github.adamw7.tools.data.source.file.CSVDataSource;
import io.github.adamw7.tools.data.source.file.InMemoryCSVDataSource;
import io.github.adamw7.tools.data.source.file.InMemoryJSONDataSource;
import io.github.adamw7.tools.data.source.file.IterableJSONDataSource;
import io.github.adamw7.tools.data.source.file.IterableTOONDataSource;
import io.github.adamw7.tools.data.source.file.IterableYAMLDataSource;
import io.github.adamw7.tools.data.source.interfaces.ColumnarDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;
import io.github.adamw7.tools.data.uniqueness.NoMemoryUniquenessCheck;

/**
 * Locks in the interface segregation between {@link IterableDataSource} (forward-only
 * streaming) and {@link ColumnarDataSource} (a source that also exposes its column
 * names). Before the split, {@code getColumnNames()} lived on {@code IterableDataSource}
 * and the forward-only file sources answered it with {@code null}, which let a caller
 * pass a schema-less source into a uniqueness check and hit a {@link NullPointerException}
 * deep inside the scan. Keeping the schema on the narrower contract turns that mistake
 * into a compile error; these tests guard the type relationships that make it so.
 */
class DataSourceSegregationTest {

	@Test
	void columnarSourcesExposeTheirSchema() {
		assertTrue(ColumnarDataSource.class.isAssignableFrom(CSVDataSource.class));
		assertTrue(ColumnarDataSource.class.isAssignableFrom(InMemoryCSVDataSource.class));
		assertTrue(ColumnarDataSource.class.isAssignableFrom(InMemoryJSONDataSource.class));
		assertTrue(ColumnarDataSource.class.isAssignableFrom(IterableSQLDataSource.class));
		assertTrue(ColumnarDataSource.class.isAssignableFrom(InMemorySQLDataSource.class));
	}

	@Test
	void forwardOnlyStreamingSourcesAreNotColumnar() {
		assertTrue(IterableDataSource.class.isAssignableFrom(IterableJSONDataSource.class));
		assertFalse(ColumnarDataSource.class.isAssignableFrom(IterableJSONDataSource.class));
		assertFalse(ColumnarDataSource.class.isAssignableFrom(IterableYAMLDataSource.class));
		assertFalse(ColumnarDataSource.class.isAssignableFrom(IterableTOONDataSource.class));
	}

	@Test
	void uniquenessCheckRequiresAColumnarSource() throws NoSuchMethodException {
		// The only public constructor takes a ColumnarDataSource; a bare
		// IterableDataSource (which has no columns) cannot be supplied.
		assertTrue(NoMemoryUniquenessCheck.class.getConstructor(ColumnarDataSource.class) != null);
		assertThrows(NoSuchMethodException.class,
				() -> NoMemoryUniquenessCheck.class.getConstructor(IterableDataSource.class));
	}
}

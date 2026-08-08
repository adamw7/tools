package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

/**
 * The forward-only contract every {@link AbstractIterableFileSource} keeps,
 * asserted once for all of its formats. Opening twice, reading before opening
 * and restarting an iteration are guarded in that shared base, not in the JSON,
 * YAML or TOON source, so each format's own test was re-proving the same code —
 * along with the {@code drain} and {@code collect} loops every one of them needs
 * to say anything about a source that only goes forwards.
 * <p>
 * A subclass names its format through {@link #newSource()} and is left with what
 * only its parser can be asked: how it flattens that format's documents.
 */
abstract class AbstractIterableDataSourceTest {

	/** A file-backed source over the fixture for the format under test. */
	protected abstract IterableDataSource newSource();

	@Test
	void resetRestartsTheIteration() throws IOException {
		try (IterableDataSource source = newSource()) {
			source.open();
			int first = drain(source);
			source.reset();

			assertEquals(first, drain(source));
		}
	}

	@Test
	void openTwiceThrows() throws IOException {
		try (IterableDataSource source = newSource()) {
			source.open();

			assertThrows(IllegalStateException.class, source::open);
		}
	}

	@Test
	void nextRowBeforeOpenThrows() throws IOException {
		try (IterableDataSource source = newSource()) {
			assertThrows(IllegalStateException.class, source::nextRow);
		}
	}

	@Test
	void hasMoreDataBeforeOpenThrows() throws IOException {
		try (IterableDataSource source = newSource()) {
			assertThrows(IllegalStateException.class, source::hasMoreData);
		}
	}

	@Test
	void nextRowsRejectsNonPositiveBatchSize() throws IOException {
		try (IterableDataSource source = newSource()) {
			source.open();

			assertThrows(IllegalArgumentException.class, () -> source.nextRows(0));
			assertThrows(IllegalArgumentException.class, () -> source.nextRows(-1));
		}
	}

	/** Counts the rows an open source has left, so an iteration can be compared to another. */
	protected int drain(IterableDataSource source) {
		int rows = 0;
		while (source.hasMoreData()) {
			if (source.nextRow() != null) {
				rows++;
			}
		}
		return rows;
	}

	/** Reads a source to exhaustion into the flattened {@code key -> value} pairs it emits. */
	protected Map<String, String> collect(IterableDataSource source) throws IOException {
		try (source) {
			source.open();
			Map<String, String> map = new HashMap<>();
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					map.put(row[0], row[1]);
				}
			}
			return map;
		}
	}
}

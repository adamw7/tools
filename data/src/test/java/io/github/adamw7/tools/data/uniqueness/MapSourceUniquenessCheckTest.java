package io.github.adamw7.tools.data.uniqueness;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.adamw7.tools.data.source.file.InMemoryJSONDataSource;
import io.github.adamw7.tools.data.source.file.InMemoryTOONDataSource;
import io.github.adamw7.tools.data.source.file.InMemoryYAMLDataSource;
import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;

/**
 * Regression tests for running {@link InMemoryUniquenessCheck} against the
 * map-backed in-memory sources (JSON/YAML/TOON). These used to fail with
 * {@code IllegalStateException: DataSource is already open} because the check
 * opens the source and then calls {@code readAll()}, which re-opened it.
 */
public class MapSourceUniquenessCheckTest {

	static Stream<Arguments> sources() {
		return Stream.of(
				Arguments.of("json", (Function<String, InMemoryDataSource>) InMemoryJSONDataSource::new),
				Arguments.of("yaml", (Function<String, InMemoryDataSource>) InMemoryYAMLDataSource::new),
				Arguments.of("toon", (Function<String, InMemoryDataSource>) InMemoryTOONDataSource::new));
	}

	private static String fileFor(String format) {
		return "src/test/resources/test." + format;
	}

	/** Every flattened key is distinct, so the {@code key} column is a key of the document. */
	@ParameterizedTest
	@MethodSource("sources")
	void keyColumnIsUnique(String format, Function<String, InMemoryDataSource> factory) {
		InMemoryDataSource source = factory.apply(fileFor(format));
		InMemoryUniquenessCheck check = new InMemoryUniquenessCheck(source);

		Result result = check.exec("key");

		assertTrue(result.isUnique());
	}

	@ParameterizedTest
	@MethodSource("sources")
	void multiColumnDoesNotCrash(String format, Function<String, InMemoryDataSource> factory) {
		InMemoryDataSource source = factory.apply(fileFor(format));
		InMemoryUniquenessCheck check = new InMemoryUniquenessCheck(source);
		String[] columns = source.getColumnNames();

		Result result = check.exec(columns[0], columns[1]);

		assertNotNull(result);
	}

	/**
	 * The source used to name every flattened key as a column while emitting two-column
	 * {@code {key, value}} rows, so checking all columns of a document with more than two
	 * keys indexed past the end of a row. It now names the two columns its rows have.
	 */
	@ParameterizedTest
	@MethodSource("sources")
	void execForAllColumnsStaysWithinRowArity(String format, Function<String, InMemoryDataSource> factory) {
		InMemoryUniquenessCheck check = new InMemoryUniquenessCheck(factory.apply(fileFor(format)));

		Result result = check.execForAllColumns();

		assertTrue(result.isUnique());
	}

	/**
	 * Regression for the double-open bug: {@code execForAllColumns()} opened the
	 * source to read its column names and then delegated to {@code exec()}, which
	 * opened it a second time. The open-once map-backed sources rejected that with
	 * {@code IllegalStateException: DataSource is already open}.
	 */
	@Test
	void execForAllColumnsDoesNotReopenMapSource() {
		InputStream json = new ByteArrayInputStream(
				"{\"a\":\"1\",\"b\":\"2\"}".getBytes(StandardCharsets.UTF_8));
		InMemoryUniquenessCheck check = new InMemoryUniquenessCheck(new InMemoryJSONDataSource(json));

		Result result = check.execForAllColumns();

		assertNotNull(result);
	}
}

package io.github.adamw7.tools.data.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.source.file.CSVDataSource;
import io.github.adamw7.tools.data.source.file.IterableJSONDataSource;
import io.github.adamw7.tools.data.source.file.IterableTOONDataSource;
import io.github.adamw7.tools.data.source.file.IterableYAMLDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;
import io.github.adamw7.tools.data.uniqueness.AbstractUniqueness;
import io.github.adamw7.tools.data.uniqueness.NoMemoryUniquenessCheck;
import io.github.adamw7.tools.data.uniqueness.Result;

public class MemoryLeakTest {
	private final static Logger log = LogManager.getLogger(MemoryLeakTest.class.getName());

	private static final String PARENT = new File(getSourceLocation()).getParent();

	private static final String FILE_NAME = PARENT + File.separator + "random_data.csv";
	private static final String JSON_FILE_NAME = PARENT + File.separator + "random_data.json";
	private static final String YAML_FILE_NAME = PARENT + File.separator + "random_data.yaml";
	private static final String TOON_FILE_NAME = PARENT + File.separator + "random_data.toon";

	private static final int ROWS = 50_000;
	private static final int SEED = 500;

	private static final int VALUE_LENGTH = 12;

	/**
	 * The iterable JSON, YAML and TOON sources emit one row at a time without holding the
	 * document in memory. To prove that, each generated document has far more fields than
	 * could be buffered under the tight surefire heap (-Xmx16m): a non-iterable reader
	 * would run out of memory, while a single pass stays flat.
	 */
	private static final int ITERABLE_FIELDS = 100_000;

	private static String getSourceLocation() {
		return MemoryLeakTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
	}

	@BeforeAll
	public static void setUp() {
		log.info("Max heap is set to {}", Runtime.getRuntime().maxMemory());
		prepareData();
	}

	private static void prepareData() {
		int[] columnLengths = { 10, 15, 8, 12 };

		createCSV(FILE_NAME, ROWS, columnLengths);
		log.info("CSV file {} with random data created.", FILE_NAME);

		createJSON(JSON_FILE_NAME, ITERABLE_FIELDS);
		log.info("JSON file {} with random data created.", JSON_FILE_NAME);

		createKeyValueFile(YAML_FILE_NAME, ITERABLE_FIELDS);
		log.info("YAML file {} with random data created.", YAML_FILE_NAME);

		createKeyValueFile(TOON_FILE_NAME, ITERABLE_FIELDS);
		log.info("TOON file {} with random data created.", TOON_FILE_NAME);
	}

	public static void createCSV(String filename, int numRows, int[] columnLengths) {
		try (FileWriter csvWriter = new FileWriter(filename)) {
			csvWriter.append("Column 1,Column 2,Column 3,Column 4\n");

			Random random = new Random(SEED);

			for (int i = 0; i < numRows; i++) {
				StringBuilder rowBuilder = new StringBuilder();

				for (int length : columnLengths) {
					String randomData = generateRandomData(length, random);
					rowBuilder.append(randomData).append(",");
				}

				csvWriter.append(rowBuilder.substring(0, rowBuilder.length() - 1)).append("\n");
			}
		} catch (IOException e) {
			log.error(e);
		}
	}

	public static void createJSON(String filename, int numFields) {
		try (FileWriter writer = new FileWriter(filename)) {
			Random random = new Random(SEED);
			writer.append("{");
			appendJSONFields(writer, numFields, random);
			writer.append("}");
		} catch (IOException e) {
			log.error(e);
		}
	}

	private static void appendJSONFields(FileWriter writer, int numFields, Random random) throws IOException {
		for (int i = 0; i < numFields; i++) {
			String separator = (i == 0) ? "" : ",";
			writer.append(separator).append("\"field").append(String.valueOf(i)).append("\":\"")
					.append(generateRandomData(VALUE_LENGTH, random)).append("\"");
		}
	}

	public static void createKeyValueFile(String filename, int numFields) {
		try (FileWriter writer = new FileWriter(filename)) {
			Random random = new Random(SEED);
			appendKeyValueLines(writer, numFields, random);
		} catch (IOException e) {
			log.error(e);
		}
	}

	private static void appendKeyValueLines(FileWriter writer, int numFields, Random random) throws IOException {
		for (int i = 0; i < numFields; i++) {
			writer.append("field").append(String.valueOf(i)).append(": ")
					.append(generateRandomData(VALUE_LENGTH, random)).append("\n");
		}
	}

	public static String generateRandomData(int length, Random random) {
		String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
		StringBuilder randomData = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			randomData.append(characters.charAt(random.nextInt(characters.length())));
		}
		return randomData.toString();
	}

	@Test
	public void seekMemoryLeakInCSVSource() {
		try {
			IterableDataSource source = new CSVDataSource(FILE_NAME);
			source.open();
			int i = 0;
			while (source.hasMoreData()) {
				String[] nextRow = source.nextRow();
				if (nextRow != null) {
					++i;
				}
			}
			source.close();
			assertEquals(ROWS + 1, i); // one for columns
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	public void seekMemoryLeakInJSONSource() {
		seekMemoryLeakInIterableSource(new IterableJSONDataSource(JSON_FILE_NAME));
	}

	@Test
	public void seekMemoryLeakInYAMLSource() {
		seekMemoryLeakInIterableSource(new IterableYAMLDataSource(YAML_FILE_NAME));
	}

	@Test
	public void seekMemoryLeakInTOONSource() {
		seekMemoryLeakInIterableSource(new IterableTOONDataSource(TOON_FILE_NAME));
	}

	private void seekMemoryLeakInIterableSource(IterableDataSource source) {
		try {
			source.open();
			assertEquals(ITERABLE_FIELDS, countRows(source));
		} finally {
			close(source);
		}
	}

	private int countRows(IterableDataSource source) {
		int rows = 0;
		while (source.hasMoreData()) {
			String[] nextRow = source.nextRow();
			if (nextRow != null) {
				++rows;
			}
		}
		return rows;
	}

	private void close(IterableDataSource source) {
		try {
			source.close();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	public void seekMemoryLeakInUniquenessCheck() {
		try {
			IterableDataSource source = new CSVDataSource(FILE_NAME, 1);
			AbstractUniqueness uniqueness = new NoMemoryUniquenessCheck(source);

			Result result = uniqueness.exec("Column 1");
			source.close();
			assertTrue(result.isUnique());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@AfterAll
	public static void tearDown() {
		new File(FILE_NAME).delete();
		new File(JSON_FILE_NAME).delete();
		new File(YAML_FILE_NAME).delete();
		new File(TOON_FILE_NAME).delete();
	}
}

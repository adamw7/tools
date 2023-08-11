package io.github.adamw7.tools.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class MemoryLeakTest {
	private final static Logger log = LogManager.getLogger(MemoryLeakTest.class.getName());

	private static final String FILE_NAME = new File(MemoryLeakTest.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() + "random_data.csv";
	private static final int ROWS = 100_000;

	@BeforeAll
	public static void setUp() {
		log.info("Max heap is set to {}", Runtime.getRuntime().maxMemory());
		prepareData();
	}

	private static void prepareData() {
		int[] columnLengths = { 10, 15, 8, 12 };

		createCSV(FILE_NAME, ROWS, columnLengths);
		
		log.info("CSV file {} with random data created.", FILE_NAME);
	}

	public static void createCSV(String filename, int numRows, int[] columnLengths) {
		try (FileWriter csvWriter = new FileWriter(filename)) {
			csvWriter.append("Column 1,Column 2,Column 3,Column 4\n");

			Random random = new Random();

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

	public static String generateRandomData(int length, Random random) {
		String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
		StringBuilder randomData = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			randomData.append(characters.charAt(random.nextInt(characters.length())));
		}
		return randomData.toString();
	}

	@Test
	public void seekMemoryLeak() {
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

	@AfterAll
	public static void tearDown() {
		new File(FILE_NAME).delete();
	}
}

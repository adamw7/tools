package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;

public class TOONDataSourceTest {

	@Test
	public void testGetColumnNames() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			String[] columnNames = source.getColumnNames();
			assertNotNull(columnNames);
			assertTrue(columnNames.length > 27, "Expected more than 27 columns after extending test data");
		}
	}

	@Test
	public void testIterateData() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			int rowCount = 0;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					rowCount++;
					assertEquals(2, row.length);
					assertNotNull(row[0]);
					assertNotNull(row[1]);
				}
			}
			assertTrue(rowCount > 27, "Expected more than 27 rows after extending test data");
		}
	}

	@Test
	public void testReadAll() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			List<String[]> data = source.readAll();
			assertNotNull(data);
			assertFalse(data.isEmpty());
			for (String[] row : data) {
				assertEquals(2, row.length);
				assertNotNull(row[0]);
				assertNotNull(row[1]);
			}
		}
	}

	@Test
	public void testReset() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			// Read all data
			while (source.hasMoreData()) {
				source.nextRow();
			}

			// Reset and verify we can read again
			source.reset();
			assertTrue(source.hasMoreData());
			String[] row = source.nextRow();
			assertNotNull(row);
		}
	}

	@Test
	public void testOpenTwiceThrowsException() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();
			assertThrows(IllegalStateException.class, () -> source.open());
		}
	}

	@Test
	public void testNextRowBeforeOpenThrowsException() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			assertThrows(IllegalStateException.class, () -> source.nextRow());
		}
	}

	@Test
	public void testHasMoreDataBeforeOpenThrowsException() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			assertThrows(IllegalStateException.class, () -> source.hasMoreData());
		}
	}

	@Test
	public void testPrimitiveArray() throws IOException {
		String toon = "tags[3]: admin,ops,dev";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			String[] columnNames = source.getColumnNames();
			assertTrue(columnNames.length > 0);

			// Should contain the array elements
			boolean foundAdmin = false;
			boolean foundOps = false;
			boolean foundDev = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[1] != null) {
					if (row[1].equals("admin")) foundAdmin = true;
					if (row[1].equals("ops")) foundOps = true;
					if (row[1].equals("dev")) foundDev = true;
				}
			}
			assertTrue(foundAdmin);
			assertTrue(foundOps);
			assertTrue(foundDev);
		}
	}

	@Test
	public void testTabularArray() throws IOException {
		String toon = "users[2]{id,name,role}:\n  1,Alice,admin\n  2,Bob,user";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean foundAlice = false;
			boolean foundBob = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[1] != null) {
					if (row[1].equals("Alice")) foundAlice = true;
					if (row[1].equals("Bob")) foundBob = true;
				}
			}
			assertTrue(foundAlice);
			assertTrue(foundBob);
		}
	}

	@Test
	public void testNestedObject() throws IOException {
		String toon = "context:\n  task: Our favorite hikes\n  location: Boulder";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean foundTask = false;
			boolean foundLocation = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null) {
					if (row[0].equals("context.task") && row[1].equals("Our favorite hikes")) foundTask = true;
					if (row[0].equals("context.location") && row[1].equals("Boulder")) foundLocation = true;
				}
			}
			assertTrue(foundTask);
			assertTrue(foundLocation);
		}
	}

	@Test
	public void testQuotedStrings() throws IOException {
		String toon = "message: \"Hello, World!\"";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean found = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0].equals("message") && row[1].equals("Hello, World!")) {
					found = true;
				}
			}
			assertTrue(found);
		}
	}

	@Test
	public void testIterator() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			var iterator = source.iterator();
			assertNotNull(iterator);
			assertTrue(iterator.hasNext());

			int count = 0;
			while (iterator.hasNext()) {
				String[] row = iterator.next();
				assertNotNull(row);
				count++;
			}
			assertTrue(count > 0);
		}
	}

	@Test
	public void testSimpleKeyValuePairs() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			boolean foundAppName = false;
			boolean foundVersion = false;
			boolean foundIsEnabled = false;
			boolean foundIsDisabled = false;
			boolean foundMaxRetries = false;
			boolean foundPi = false;

			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("appName") && row[1].equals("MyTestApp")) foundAppName = true;
					if (row[0].equals("version") && row[1].equals("1.2.3")) foundVersion = true;
					if (row[0].equals("isEnabled") && row[1].equals("true")) foundIsEnabled = true;
					if (row[0].equals("isDisabled") && row[1].equals("false")) foundIsDisabled = true;
					if (row[0].equals("maxRetries") && row[1].equals("5")) foundMaxRetries = true;
					if (row[0].equals("pi") && row[1].equals("3.14159")) foundPi = true;
				}
			}

			assertTrue(foundAppName, "Expected to find appName=MyTestApp");
			assertTrue(foundVersion, "Expected to find version=1.2.3");
			assertTrue(foundIsEnabled, "Expected to find isEnabled=true");
			assertTrue(foundIsDisabled, "Expected to find isDisabled=false");
			assertTrue(foundMaxRetries, "Expected to find maxRetries=5");
			assertTrue(foundPi, "Expected to find pi=3.14159");
		}
	}

	@Test
	public void testNestedObjectFromFile() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			boolean foundHost = false;
			boolean foundPort = false;
			boolean foundUsername = false;
			boolean foundPassword = false;

			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("database.host") && row[1].equals("localhost")) foundHost = true;
					if (row[0].equals("database.port") && row[1].equals("5432")) foundPort = true;
					if (row[0].equals("database.credentials.username") && row[1].equals("admin")) foundUsername = true;
					if (row[0].equals("database.credentials.password") && row[1].equals("secret123")) foundPassword = true;
				}
			}

			assertTrue(foundHost, "Expected to find database.host=localhost");
			assertTrue(foundPort, "Expected to find database.port=5432");
			assertTrue(foundUsername, "Expected to find database.credentials.username=admin");
			assertTrue(foundPassword, "Expected to find database.credentials.password=secret123");
		}
	}

	@Test
	public void testQuotedStringsFromFile() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			boolean foundGreeting = false;
			boolean foundPathWithSpaces = false;
			boolean foundQuotedValue = false;

			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("greeting") && row[1].equals("Hello, World!")) foundGreeting = true;
					if (row[0].equals("pathWithSpaces") && row[1].equals("C:\\Program Files\\App")) foundPathWithSpaces = true;
					if (row[0].equals("quotedValue") && row[1].equals("She said \"Hello\" to me")) foundQuotedValue = true;
				}
			}

			assertTrue(foundGreeting, "Expected to find greeting=Hello, World!");
			assertTrue(foundPathWithSpaces, "Expected to find pathWithSpaces with backslash escape");
			assertTrue(foundQuotedValue, "Expected to find quotedValue with escaped quotes");
		}
	}

	@Test
	public void testEscapeSequences() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			boolean foundMultilineHint = false;
			boolean foundTabSeparated = false;

			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("multilineHint") && row[1].equals("Line1\nLine2\nLine3")) foundMultilineHint = true;
					if (row[0].equals("tabSeparated") && row[1].equals("col1\tcol2\tcol3")) foundTabSeparated = true;
				}
			}

			assertTrue(foundMultilineHint, "Expected to find multilineHint with newline escapes");
			assertTrue(foundTabSeparated, "Expected to find tabSeparated with tab escapes");
		}
	}

	@Test
	public void testNestedArrayFromFile() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			boolean foundHigh = false;
			boolean foundMedium = false;
			boolean foundLow = false;
			boolean foundCount = false;

			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("priorities") && row[1].equals("3")) foundCount = true;
					if (row[0].equals("priorities[0]") && row[1].equals("high")) foundHigh = true;
					if (row[0].equals("priorities[1]") && row[1].equals("medium")) foundMedium = true;
					if (row[0].equals("priorities[2]") && row[1].equals("low")) foundLow = true;
				}
			}

			assertTrue(foundCount, "Expected to find priorities count=3");
			assertTrue(foundHigh, "Expected to find priorities[0]=high");
			assertTrue(foundMedium, "Expected to find priorities[1]=medium");
			assertTrue(foundLow, "Expected to find priorities[2]=low");
		}
	}

	@Test
	public void testTabularArrayWithQuotedValues() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			boolean foundProduct1Sku = false;
			boolean foundProduct1Name = false;
			boolean foundProduct1Price = false;
			boolean foundProduct1Desc = false;
			boolean foundProduct2Sku = false;
			boolean foundProduct2Name = false;

			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("products[0].sku") && row[1].equals("SKU001")) foundProduct1Sku = true;
					if (row[0].equals("products[0].name") && row[1].equals("Widget Pro")) foundProduct1Name = true;
					if (row[0].equals("products[0].price") && row[1].equals("29.99")) foundProduct1Price = true;
					if (row[0].equals("products[0].description") && row[1].equals("A great widget, really!")) foundProduct1Desc = true;
					if (row[0].equals("products[1].sku") && row[1].equals("SKU002")) foundProduct2Sku = true;
					if (row[0].equals("products[1].name") && row[1].equals("Gadget Plus")) foundProduct2Name = true;
				}
			}

			assertTrue(foundProduct1Sku, "Expected to find products[0].sku=SKU001");
			assertTrue(foundProduct1Name, "Expected to find products[0].name=Widget Pro");
			assertTrue(foundProduct1Price, "Expected to find products[0].price=29.99");
			assertTrue(foundProduct1Desc, "Expected to find products[0].description with comma inside quotes");
			assertTrue(foundProduct2Sku, "Expected to find products[1].sku=SKU002");
			assertTrue(foundProduct2Name, "Expected to find products[1].name=Gadget Plus");
		}
	}

	@Test
	public void testNumericValuesInTabularArray() throws IOException {
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(Utils.getFileName("test.toon"))) {
			source.open();

			boolean foundTemperature = false;
			boolean foundTemperatureValue = false;
			boolean foundHumidity = false;
			boolean foundPressure = false;
			boolean foundPressureValue = false;

			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("metrics[0].name") && row[1].equals("temperature")) foundTemperature = true;
					if (row[0].equals("metrics[0].value") && row[1].equals("72.5")) foundTemperatureValue = true;
					if (row[0].equals("metrics[1].name") && row[1].equals("humidity")) foundHumidity = true;
					if (row[0].equals("metrics[2].name") && row[1].equals("pressure")) foundPressure = true;
					if (row[0].equals("metrics[2].value") && row[1].equals("1013.25")) foundPressureValue = true;
				}
			}

			assertTrue(foundTemperature, "Expected to find metrics[0].name=temperature");
			assertTrue(foundTemperatureValue, "Expected to find metrics[0].value=72.5 (decimal)");
			assertTrue(foundHumidity, "Expected to find metrics[1].name=humidity");
			assertTrue(foundPressure, "Expected to find metrics[2].name=pressure");
			assertTrue(foundPressureValue, "Expected to find metrics[2].value=1013.25 (decimal)");
		}
	}

	@Test
	public void testBooleanValues() throws IOException {
		String toon = "enabled: true\ndisabled: false";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean foundTrue = false;
			boolean foundFalse = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("enabled") && row[1].equals("true")) foundTrue = true;
					if (row[0].equals("disabled") && row[1].equals("false")) foundFalse = true;
				}
			}
			assertTrue(foundTrue, "Expected to find enabled=true");
			assertTrue(foundFalse, "Expected to find disabled=false");
		}
	}

	@Test
	public void testIntegerValues() throws IOException {
		String toon = "count: 42\nnegative: -10\nzero: 0";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean foundCount = false;
			boolean foundNegative = false;
			boolean foundZero = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("count") && row[1].equals("42")) foundCount = true;
					if (row[0].equals("negative") && row[1].equals("-10")) foundNegative = true;
					if (row[0].equals("zero") && row[1].equals("0")) foundZero = true;
				}
			}
			assertTrue(foundCount, "Expected to find count=42");
			assertTrue(foundNegative, "Expected to find negative=-10");
			assertTrue(foundZero, "Expected to find zero=0");
		}
	}

	@Test
	public void testDecimalValues() throws IOException {
		String toon = "pi: 3.14159\nsmall: 0.001\nlarge: 12345.6789";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean foundPi = false;
			boolean foundSmall = false;
			boolean foundLarge = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("pi") && row[1].equals("3.14159")) foundPi = true;
					if (row[0].equals("small") && row[1].equals("0.001")) foundSmall = true;
					if (row[0].equals("large") && row[1].equals("12345.6789")) foundLarge = true;
				}
			}
			assertTrue(foundPi, "Expected to find pi=3.14159");
			assertTrue(foundSmall, "Expected to find small=0.001");
			assertTrue(foundLarge, "Expected to find large=12345.6789");
		}
	}

	@Test
	public void testDeeplyNestedObject() throws IOException {
		String toon = "level1:\n  level2:\n    level3:\n      value: deep";
		try (InMemoryTOONDataSource source = new InMemoryTOONDataSource(
				new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8)))) {
			source.open();

			boolean foundDeep = false;
			while (source.hasMoreData()) {
				String[] row = source.nextRow();
				if (row != null && row[0] != null && row[1] != null) {
					if (row[0].equals("level1.level2.level3.value") && row[1].equals("deep")) {
						foundDeep = true;
					}
				}
			}
			assertTrue(foundDeep, "Expected to find level1.level2.level3.value=deep");
		}
	}
}

package io.github.adamw7.tools.data.compression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.data.Utils;

public class ZipUtilsTest {

	@Test
	public void testNotZipped() {
		String industryFile = Utils.getIndustryFile();
		try {
			FileInputStream fileInputStream = new FileInputStream(industryFile);
			InputStream inputStream = ZipUtils.unzipIfNeeded(fileInputStream, industryFile);
			assertEquals(inputStream, fileInputStream);
		} catch (FileNotFoundException e) {
			fail(e);
		}
	}

	@Test
	public void testZipped() {
		String zippedIndustryFile = Utils.getFileName("industry_sic.csv.gz");
		try {
			InputStream inputStream = ZipUtils.unzipIfNeeded(new FileInputStream(zippedIndustryFile),
					zippedIndustryFile);
			assertNotNull(inputStream);
			assertEquals(GZIPInputStream.class, inputStream.getClass());
			GZIPInputStream gzipInputStream = (GZIPInputStream) inputStream;
			byte[] bytes = gzipInputStream.readAllBytes();
			String content = new String(bytes);
			String expectedContent = "SIC Code,Description";
			assertEquals(expectedContent, content.substring(0, expectedContent.length()));
		} catch (Exception e) {
			fail(e);
		}
	}
	
	@Test
	public void testAlreadyGZippedStream() {		
		String zippedIndustryFile = Utils.getFileName("industry_sic.csv.gz");

		try {
			GZIPInputStream fileInputStream = new GZIPInputStream(new FileInputStream(zippedIndustryFile));
			InputStream inputStream = ZipUtils.unzipIfNeeded(fileInputStream, zippedIndustryFile);
			assertEquals(inputStream, fileInputStream);
		} catch (IOException e) {
			fail(e);
		}
	}
}

package io.github.adamw7.tools.data.compression;

import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class ZipUtils {

	public static InputStream unzipIfNeeded(InputStream stream, String fileName, String regex) {
		if (isZipped(fileName, regex) && !(stream instanceof GZIPInputStream)) {
			try {
				return new GZIPInputStream(stream);
			} catch (Exception e) {
				throw new RuntimeException("Error creating GZIP stream", e);
			}
		} else {
			return stream;
		}
	}

	public static InputStream unzipIfNeeded(InputStream stream, String fileName) {
		return unzipIfNeeded(stream, fileName, "");
	}

	private static boolean isZipped(String fileName, String regex) {
		return fileName.matches(regex) || fileName.toLowerCase().endsWith("gz")
				|| fileName.toLowerCase().endsWith("zip");
	}
}

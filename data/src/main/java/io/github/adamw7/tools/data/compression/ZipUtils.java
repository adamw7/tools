package io.github.adamw7.tools.data.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ZipUtils {

	private final static Logger log = LogManager.getLogger(ZipUtils.class.getName());

	private ZipUtils() {}
	
	public static InputStream unzipIfNeeded(InputStream stream, String fileName) {
		if (isZipped(fileName) && !(stream instanceof GZIPInputStream)) {
			try {
				return new GZIPInputStream(stream);
			} catch (IOException e) {
				throw new UncheckedIOException("Error creating GZIP stream", e);
			}
		} else {
			return stream;
		}
	}

	private static boolean isZipped(String fileName) {
		if (fileName != null) {
			try (RandomAccessFile raf = new RandomAccessFile(fileName, "r")) {
				byte first = raf.readByte();
				byte second = raf.readByte();

				return ((first == (byte) (GZIPInputStream.GZIP_MAGIC))
						&& (second == (byte) (GZIPInputStream.GZIP_MAGIC >> 8)));
			} catch (IOException e) {
				log.error("Error checking if " + fileName + " is zipped", e);
				return false;
			}			
		} else {
			return false;
		}
	
	}
}

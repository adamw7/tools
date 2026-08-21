package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;
import io.github.adamw7.tools.data.uniqueness.NoMemoryUniquenessCheck;

/**
 * Covers the one thing every {@link java.util.Scanner}-backed source shares: a read
 * that fails part-way through must be reported, not mistaken for the end of the data.
 * Scanner swallows the {@link IOException} and simply stops producing tokens, so
 * without the check in {@link AbstractFileSource#hasNextLine()} a truncated transfer,
 * a corrupt GZip member or a disk error reads as a short but successful file. The same
 * corrupt GZip member is what leaks a descriptor when the wrapping fails before any
 * Scanner owns the opened file, which one test pins. The name a source answers for the
 * file it reads is pinned here too, that being the other thing every one of them shares,
 * along with where the shared read-everything helper is allowed to show up: publicly on
 * the in-memory sources that promise it, nowhere else.
 */
public class AbstractFileSourceTest {

	private static final String HEADER = "id,name\n";

	@Test
	public void csvSourceFailsWhenTheReadBreaksMidFile() {
		CSVDataSource source = new CSVDataSource(failingAfter(HEADER + "1,Adam\n"), ",", 1);
		source.open();

		UncheckedIOException thrown = assertThrows(UncheckedIOException.class, () -> readAll(source));

		assertEquals("disk went away", thrown.getCause().getMessage());
		assertTrue(thrown.getMessage().contains("the input stream"), thrown.getMessage());
	}

	@Test
	public void csvSourceNamesTheFileItFailedToRead(@TempDir Path directory) throws IOException {
		Path file = truncatedGzip(directory);
		CSVDataSource source = new CSVDataSource(file.toString(), ",", 1, AllowedPaths.under(directory));
		source.open();

		UncheckedIOException thrown = assertThrows(UncheckedIOException.class, () -> readAll(source));

		// A GZip member cut short ends with an unexpected EOF rather than a clean end of
		// data; the message must name the file so the failure can be traced to it.
		assertInstanceOf(EOFException.class, thrown.getCause());
		assertTrue(thrown.getMessage().contains(file.toString()), thrown.getMessage());
	}

	@Test
	public void uniquenessCheckFailsRatherThanCallingAHalfReadColumnUnique() {
		// The duplicate sits in the part of the file the read never reaches, so a
		// swallowed failure would answer "unique" — wrong in the dangerous direction.
		CSVDataSource source = new CSVDataSource(failingAfter(HEADER + "1,Adam\n"), ",", 1);

		assertThrows(UncheckedIOException.class, () -> new NoMemoryUniquenessCheck(source).exec("id"));
	}

	@Test
	public void jacksonSourceFailsWhenTheReadBreaksMidDocument() {
		// The map sources parse in their constructor, so the failure surfaces there.
		assertThrows(UncheckedIOException.class,
				() -> new InMemoryJSONDataSource(failingAfter("{\"id\": \"1\",\n")));
	}

	@Test
	public void toonSourceFailsWhenTheReadBreaksMidDocument() {
		assertThrows(UncheckedIOException.class, () -> new InMemoryTOONDataSource(failingAfter("id: 1\n")));
	}

	@Test
	public void cleanEndOfFileIsStillAnEndOfFile() {
		CSVDataSource source = new CSVDataSource(stream(HEADER + "1,Adam\n2,Adam\n"), ",", 1);
		source.open();

		// Nothing failed, so ioException() is null and the read ends normally.
		assertEquals(2, readAll(source));
	}

	@Test
	public void theOpenedFileIsClosedWhenTheGZipWrappingFails(@TempDir Path directory) throws IOException {
		Path file = gzipMagicWithoutAMember(directory);
		RecordingSource source = new RecordingSource(file.toString());

		assertThrows(UncheckedIOException.class, () -> source.createScanner(file.toString()));

		// No Scanner was built, so nothing else owns the descriptor: createScanner had to
		// close it itself. A closed FileInputStream refuses to read.
		assertThrows(IOException.class, () -> source.openedStream.read());
	}

	@Test
	public void theFileNameIsTheLastElementOfThePath() {
		RecordingSource source = new RecordingSource(Path.of("data", "rows.csv").toString());

		assertEquals("rows.csv", source.getFileName());
	}

	@Test
	public void aPathThatNamesNoFileIsRefusedRatherThanDereferenced() {
		// A filesystem root has no last element, so getFileName() answers null and the
		// lookup would throw NPE two lines after the method took care to say what was
		// wrong. File.separator is that root on both POSIX and Windows.
		RecordingSource source = new RecordingSource(File.separator);

		IllegalStateException thrown = assertThrows(IllegalStateException.class, source::getFileName);

		assertTrue(thrown.getMessage().contains("names no file"), thrown.getMessage());
	}

	@Test
	public void aSourceBackedByARawStreamStillSaysSoRatherThanNamingAFile() {
		CSVDataSource source = new CSVDataSource(stream(HEADER), ",", 1);

		IllegalStateException thrown = assertThrows(IllegalStateException.class, source::getFileName);

		assertTrue(thrown.getMessage().contains("raw input stream"), thrown.getMessage());
	}

	@Test
	public void theSharedReadEverythingHelperStaysProtected() throws NoSuchMethodException {
		Method helper = AbstractFileSource.class.getDeclaredMethod("readAllRows");

		// Named readAllRows rather than readAll so that implementing InMemoryDataSource
		// never widens it: a subclass publishes the operation by writing readAll itself.
		assertTrue(Modifier.isProtected(helper.getModifiers()), "readAllRows must stay protected");
		assertFalse(Modifier.isPublic(helper.getModifiers()), "readAllRows must not be public");
	}

	@Test
	public void onlyInMemorySourcesPublishReadAll() {
		assertThrows(NoSuchMethodException.class, () -> CSVDataSource.class.getMethod("readAll"),
				"readAll must stay off the surface of the forward-only CSV source");
		assertThrows(NoSuchMethodException.class, () -> AbstractFileSource.class.getMethod("readAll"),
				"readAll must stay off the surface of the file-source base class");

		assertDeclaresPublicReadAll(InMemoryCSVDataSource.class);
		assertDeclaresPublicReadAll(AbstractInMemoryMapDataSource.class);
	}

	private static void assertDeclaresPublicReadAll(Class<? extends InMemoryDataSource> source) {
		Method readAll = assertDoesNotThrow(() -> source.getMethod("readAll"), source.getSimpleName());
		assertTrue(Modifier.isPublic(readAll.getModifiers()), source.getSimpleName() + "#readAll must be public");
		assertEquals(source, readAll.getDeclaringClass(),
				source.getSimpleName() + " must declare readAll rather than inherit a widened one");
	}

	private static int readAll(CSVDataSource source) {
		int rows = 0;
		while (source.hasMoreData()) {
			String[] row = source.nextRow();
			if (row != null) {
				++rows;
			}
		}
		return rows;
	}

	private static Path truncatedGzip(Path directory) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
			gzip.write((HEADER + "1,Adam\n2,Adam\n").getBytes(StandardCharsets.UTF_8));
		}
		byte[] complete = bytes.toByteArray();
		Path file = directory.resolve("truncated.csv.gz");
		// Everything but the trailer: the magic number still says GZip, so the source
		// starts reading and only discovers the member is cut short part-way through.
		Files.write(file, Arrays.copyOf(complete, complete.length - 8));
		return file;
	}

	private static Path gzipMagicWithoutAMember(Path directory) throws IOException {
		Path file = directory.resolve("header-only.csv.gz");
		// The magic number alone: enough for the source to decide the file is GZipped, and
		// too little for GZIPInputStream to read a header, so the wrapping fails with the
		// file already open.
		Files.write(file, new byte[] { (byte) 0x1f, (byte) 0x8b });
		return file;
	}

	private static InputStream stream(String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	/** A stream that hands over {@code prefix} and then fails the way a dying disk does. */
	private static InputStream failingAfter(String prefix) {
		InputStream readable = stream(prefix);
		return new InputStream() {
			@Override
			public int read() throws IOException {
				int next = readable.read();
				if (next == -1) {
					throw new IOException("disk went away");
				}
				return next;
			}

			@Override
			public int read(byte[] buffer, int offset, int length) throws IOException {
				int read = readable.read(buffer, offset, length);
				if (read == -1) {
					throw new IOException("disk went away");
				}
				return read;
			}
		};
	}

	/**
	 * Keeps the stream {@link AbstractFileSource#createScanner(String)} opened, so a test can
	 * ask whether it was closed. The stream constructor opens nothing itself, which leaves
	 * {@code createScanner(String)} to be called on its own rather than from a constructor
	 * that throws before handing back the source.
	 */
	private static final class RecordingSource extends AbstractFileSource {

		private InputStream openedStream;

		private RecordingSource(String fileName) {
			super(InputStream.nullInputStream());
			// What ZipUtils reads the magic number from.
			this.fileName = fileName;
		}

		@Override
		protected Scanner createScanner(InputStream inputStream) {
			openedStream = inputStream;
			return super.createScanner(inputStream);
		}

		@Override
		public void open() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String[] nextRow() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasMoreData() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void reset() {
			throw new UnsupportedOperationException();
		}
	}
}

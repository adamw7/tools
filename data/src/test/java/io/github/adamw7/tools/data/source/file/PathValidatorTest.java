package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Covers what is left of the deprecated process-wide entry points: that they still
 * delegate to an {@link AllowedPaths}, and that a source built without one of its own
 * still honours a base directory set through them. The path checks themselves are
 * {@code AllowedPathsTest}'s.
 *
 * <p>Still runs alone under the class-parallel unit run, and is the only class here that
 * has to: these methods write one field for the whole JVM, so while this class points it
 * at a {@code @TempDir}, every other test constructing a file source would be refused the
 * test resource it reads. Clearing it afterwards is not enough when something else runs
 * <em>during</em>. The class goes when the methods do.</p>
 */
@Isolated
@SuppressWarnings("removal")
public class PathValidatorTest {

	@AfterEach
	public void resetBaseDir() {
		PathValidator.clearAllowedBaseDir();
	}

	@Test
	public void validatesThroughTheProcessWideBoundary() {
		String validated = PathValidator.validate("report.csv");

		assertEquals(Path.of("report.csv").toAbsolutePath().normalize().toString(), validated);
	}

	@Test
	public void rejectsTraversal() {
		assertThrows(SecurityException.class, () -> PathValidator.validate("../etc/passwd"));
	}

	@Test
	public void setBaseDirRejectsNull() {
		assertThrows(IllegalArgumentException.class, () -> PathValidator.setAllowedBaseDir(null));
	}

	@Test
	public void deniesPathOutsideBaseDir(@TempDir Path tempDir) throws IOException {
		Path baseDir = Files.createDirectory(tempDir.resolve("base"));
		PathValidator.setAllowedBaseDir(baseDir);

		Path outside = tempDir.resolve("outside.csv");
		assertThrows(SecurityException.class, () -> PathValidator.validate(outside.toString()));
	}

	@Test
	public void clearingBaseDirLiftsRestriction(@TempDir Path tempDir) throws IOException {
		Path baseDir = Files.createDirectory(tempDir.resolve("base"));
		PathValidator.setAllowedBaseDir(baseDir);
		PathValidator.clearAllowedBaseDir();

		Path outside = tempDir.resolve("outside.csv");
		String validated = PathValidator.validate(outside.toString());

		assertTrue(validated.endsWith("outside.csv"));
	}

	/**
	 * A source built through a constructor taking no {@link AllowedPaths} keeps reading
	 * within a base directory set here, so migrating to the instance boundary does not
	 * silently unconfine a host that has not migrated yet.
	 */
	@Test
	public void sourceWithoutItsOwnBoundaryHonoursTheProcessWideOne(@TempDir Path tempDir) throws IOException {
		Path baseDir = Files.createDirectory(tempDir.resolve("base"));
		Path outside = Files.writeString(tempDir.resolve("outside.csv"), "id\n1\n");
		PathValidator.setAllowedBaseDir(baseDir);

		assertThrows(SecurityException.class, () -> new InMemoryCSVDataSource(outside.toString(), 1));
	}

	/**
	 * The other half of that promise: a source handed its own boundary reads within it,
	 * whatever the process-wide one says.
	 */
	@Test
	public void sourceWithItsOwnBoundaryIgnoresTheProcessWideOne(@TempDir Path tempDir)
			throws IOException {
		Path baseDir = Files.createDirectory(tempDir.resolve("base"));
		Path elsewhere = Files.createDirectory(tempDir.resolve("elsewhere"));
		Path data = Files.writeString(elsewhere.resolve("data.csv"), "id\n1\n");
		PathValidator.setAllowedBaseDir(baseDir);

		assertOpens(data, AllowedPaths.under(elsewhere));
	}

	private void assertOpens(Path data, AllowedPaths allowedPaths) throws FileNotFoundException, IOException {
		try (InMemoryCSVDataSource source = new InMemoryCSVDataSource(data.toString(), 1, allowedPaths)) {
			source.open();
			assertEquals("id", source.getColumnNames()[0]);
		}
	}
}

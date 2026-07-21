package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PathValidatorTest {

	@AfterEach
	public void resetBaseDir() {
		PathValidator.clearAllowedBaseDir();
	}

	@Test
	public void rejectsNullPath() {
		assertThrows(IllegalArgumentException.class, () -> PathValidator.validate(null));
	}

	@Test
	public void rejectsEmptyPath() {
		assertThrows(IllegalArgumentException.class, () -> PathValidator.validate(""));
	}

	@Test
	public void rejectsBlankPath() {
		assertThrows(IllegalArgumentException.class, () -> PathValidator.validate("   "));
	}

	@Test
	public void rejectsLeadingTraversal() {
		assertThrows(SecurityException.class, () -> PathValidator.validate("../etc/passwd"));
	}

	@Test
	public void rejectsEmbeddedTraversal() {
		assertThrows(SecurityException.class, () -> PathValidator.validate("/data/uploads/../secret"));
	}

	@Test
	public void rejectsBareDotDot() {
		assertThrows(SecurityException.class, () -> PathValidator.validate(".."));
	}

	@Test
	public void rejectsBackslashTraversal() {
		assertThrows(SecurityException.class, () -> PathValidator.validate("..\\windows\\system32"));
	}

	@Test
	public void returnsCanonicalisedAbsolutePathWhenNoBaseDir() {
		String validated = PathValidator.validate("report.csv");
		Path expected = Path.of("report.csv").toAbsolutePath().normalize();
		assertEquals(expected.toString(), validated);
	}

	@Test
	public void setBaseDirRejectsNull() {
		assertThrows(IllegalArgumentException.class, () -> PathValidator.setAllowedBaseDir(null));
	}

	@Test
	public void allowsPathInsideBaseDir(@TempDir Path baseDir) throws IOException {
		PathValidator.setAllowedBaseDir(baseDir);

		Path inside = baseDir.toRealPath().resolve("report.csv");
		String validated = PathValidator.validate(inside.toString());

		assertEquals(inside.toAbsolutePath().normalize().toString(), validated);
	}

	@Test
	public void deniesPathOutsideBaseDir(@TempDir Path tempDir) throws IOException {
		Path baseDir = Files.createDirectory(tempDir.resolve("base"));
		PathValidator.setAllowedBaseDir(baseDir);

		Path outside = tempDir.resolve("outside.csv");
		assertThrows(SecurityException.class, () -> PathValidator.validate(outside.toString()));
	}

	@Test
	public void deniesSymlinkInsideBaseDirPointingOutside(@TempDir Path tempDir) throws IOException {
		Path baseDir = Files.createDirectory(tempDir.resolve("base"));
		Path secret = Files.writeString(tempDir.resolve("secret.csv"), "top,secret");
		Path link = baseDir.resolve("link.csv");
		assumeTrue(canCreateSymbolicLink(link, secret),
				"platform does not support creating symbolic links");
		PathValidator.setAllowedBaseDir(baseDir);

		assertThrows(SecurityException.class, () -> PathValidator.validate(link.toString()));
	}

	private static boolean canCreateSymbolicLink(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
			return true;
		} catch (UnsupportedOperationException | IOException e) {
			return false;
		}
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
}

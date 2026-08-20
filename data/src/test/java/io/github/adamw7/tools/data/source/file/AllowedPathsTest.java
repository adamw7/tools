package io.github.adamw7.tools.data.source.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs in the class-parallel unit suite without {@code @Isolated}: each test builds the
 * boundary it needs and nothing else in the JVM can see it, which is the point of the
 * type.
 */
public class AllowedPathsTest {

	private final AllowedPaths anywhere = AllowedPaths.anywhere();

	@Test
	public void rejectsNullPath() {
		assertThrows(IllegalArgumentException.class, () -> anywhere.validate(null));
	}

	@Test
	public void rejectsEmptyPath() {
		assertThrows(IllegalArgumentException.class, () -> anywhere.validate(""));
	}

	@Test
	public void rejectsBlankPath() {
		assertThrows(IllegalArgumentException.class, () -> anywhere.validate("   "));
	}

	@Test
	public void rejectsLeadingTraversal() {
		assertThrows(SecurityException.class, () -> anywhere.validate("../etc/passwd"));
	}

	@Test
	public void rejectsEmbeddedTraversal() {
		assertThrows(SecurityException.class, () -> anywhere.validate("/data/uploads/../secret"));
	}

	@Test
	public void rejectsTrailingTraversal() {
		assertThrows(SecurityException.class, () -> anywhere.validate("/data/uploads/.."));
	}

	@Test
	public void rejectsBareDotDot() {
		assertThrows(SecurityException.class, () -> anywhere.validate(".."));
	}

	@Test
	public void rejectsBackslashTraversal() {
		assertThrows(SecurityException.class, () -> anywhere.validate("..\\windows\\system32"));
	}

	/**
	 * A file whose name merely starts with two dots climbs out of nothing: the check
	 * looks for a {@code ..} that is a path element of its own, not for the substring.
	 */
	@Test
	public void acceptsFileNameStartingWithTwoDots(@TempDir Path baseDir) throws IOException {
		Path dotted = Files.createFile(baseDir.resolve("..name.csv"));

		String validated = AllowedPaths.under(baseDir).validate(dotted.toString());

		assertEquals(dotted.toRealPath().toString(), validated);
	}

	@Test
	public void returnsCanonicalisedAbsolutePathWhenUnconfined() {
		String validated = anywhere.validate("report.csv");

		assertEquals(Path.of("report.csv").toAbsolutePath().normalize().toString(), validated);
	}

	@Test
	public void unconfinedAllowsAnyDirectory(@TempDir Path tempDir) {
		String validated = anywhere.validate(tempDir.resolve("outside.csv").toString());

		assertTrue(validated.endsWith("outside.csv"));
	}

	@Test
	public void underRejectsNull() {
		assertThrows(IllegalArgumentException.class, () -> AllowedPaths.under(null));
	}

	@Test
	public void underRejectsMissingDirectory(@TempDir Path tempDir) {
		assertThrows(IOException.class, () -> AllowedPaths.under(tempDir.resolve("absent")));
	}

	@Test
	public void allowsPathInsideBaseDir(@TempDir Path baseDir) throws IOException {
		Path inside = baseDir.toRealPath().resolve("report.csv");

		String validated = AllowedPaths.under(baseDir).validate(inside.toString());

		assertEquals(inside.toAbsolutePath().normalize().toString(), validated);
	}

	@Test
	public void deniesPathOutsideBaseDir(@TempDir Path tempDir) throws IOException {
		Path baseDir = Files.createDirectory(tempDir.resolve("base"));
		AllowedPaths paths = AllowedPaths.under(baseDir);

		Path outside = tempDir.resolve("outside.csv");
		assertThrows(SecurityException.class, () -> paths.validate(outside.toString()));
	}

	@Test
	public void deniesSymlinkInsideBaseDirPointingOutside(@TempDir Path tempDir) throws IOException {
		Path baseDir = Files.createDirectory(tempDir.resolve("base"));
		Path secret = Files.writeString(tempDir.resolve("secret.csv"), "top,secret");
		Path link = baseDir.resolve("link.csv");
		assumeTrue(canCreateSymbolicLink(link, secret),
				"platform does not support creating symbolic links");
		AllowedPaths paths = AllowedPaths.under(baseDir);

		assertThrows(SecurityException.class, () -> paths.validate(link.toString()));
	}

	/**
	 * The boundary is per instance, which the static it replaced could not express: one
	 * validator confines to one directory while another confines to a second, and neither
	 * moves the other.
	 */
	@Test
	public void twoValidatorsHoldTwoSeparateBoundaries(@TempDir Path tempDir) throws IOException {
		Path first = Files.createDirectory(tempDir.resolve("first"));
		Path second = Files.createDirectory(tempDir.resolve("second"));
		AllowedPaths firstPaths = AllowedPaths.under(first);
		AllowedPaths secondPaths = AllowedPaths.under(second);

		String inFirst = first.toRealPath().resolve("data.csv").toString();
		String inSecond = second.toRealPath().resolve("data.csv").toString();

		assertEquals(inFirst, firstPaths.validate(inFirst));
		assertEquals(inSecond, secondPaths.validate(inSecond));
		assertThrows(SecurityException.class, () -> firstPaths.validate(inSecond));
		assertThrows(SecurityException.class, () -> secondPaths.validate(inFirst));
	}

	private static boolean canCreateSymbolicLink(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
			return true;
		} catch (UnsupportedOperationException | IOException e) {
			return false;
		}
	}
}

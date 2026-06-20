package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClassContainerTest {

	@TempDir
	Path projectRoot;

	@Test
	void exposesClassNameAndOriginalCode() {
		ClassContainer container = new ClassContainer("A.java", "public class A {}");

		assertEquals("A.java", container.className());
		assertEquals("public class A {}", container.originalCode());
	}

	@Test
	void loadReadsTheFileContentVerbatim() throws IOException {
		Path path = projectRoot.resolve("A.java");
		Files.writeString(path, "public class A {}");

		ClassContainer container = ClassContainer.load(path, "A.java");

		assertEquals("A.java", container.className());
		assertEquals("public class A {}", container.originalCode());
	}

	@Test
	void loadWrapsMissingFileInUncheckedIOException() {
		Path missing = projectRoot.resolve("Missing.java");

		assertThrows(java.io.UncheckedIOException.class, () -> ClassContainer.load(missing, "Missing.java"));
	}

	@Test
	void equalContainersShareSameNameAndCode() {
		ClassContainer one = new ClassContainer("A.java", "public class A {}");
		ClassContainer copy = new ClassContainer("A.java", "public class A {}");

		assertEquals(one, copy);
		assertEquals(one.hashCode(), copy.hashCode());
	}

	@Test
	void equalsIsReflexive() {
		ClassContainer container = new ClassContainer("A.java", "public class A {}");

		assertEquals(container, container);
	}

	@Test
	void differsWhenClassNameDiffers() {
		ClassContainer a = new ClassContainer("A.java", "public class A {}");
		ClassContainer b = new ClassContainer("B.java", "public class A {}");

		assertNotEquals(a, b);
	}

	@Test
	void differsWhenOriginalCodeDiffers() {
		ClassContainer a = new ClassContainer("A.java", "public class A {}");
		ClassContainer other = new ClassContainer("A.java", "public class A { int x; }");

		assertNotEquals(a, other);
	}

	@Test
	void isNotEqualToNullOrUnrelatedType() {
		ClassContainer container = new ClassContainer("A.java", "public class A {}");

		assertNotEquals(container, null);
		assertNotEquals(container, "A.java");
	}
}

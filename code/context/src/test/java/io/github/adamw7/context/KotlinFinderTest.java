package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KotlinFinderTest {

	@TempDir
	Path projectRoot;

	@Test
	void resolvesKotlinDependency() throws IOException {
		ClassContainer a = writeKotlin("A", "class A");
		ClassContainer b = writeKotlin("B", "class B { val a: A = A() }");
		Set<ClassContainer> allContainers = new HashSet<>(Set.of(a, b));

		Set<ClassContainer> classes = new Finder(allContainers, Language.KOTLIN).find(b, 1);

		assertEquals(2, classes.size());
		assertTrue(contains(classes, "A.kt"));
		assertTrue(contains(classes, "B.kt"));
	}

	@Test
	void ignoresJavaNamesWhenLookingForKotlinSources() throws IOException {
		ClassContainer a = writeKotlin("A", "class A");
		ClassContainer b = writeKotlin("B", "class B { val a: A = A() }");
		Set<ClassContainer> allContainers = new HashSet<>(Set.of(a, b));

		Set<ClassContainer> classes = new Finder(allContainers, Language.JAVA).find(b, 1);

		assertTrue(classes.isEmpty());
	}

	private ClassContainer writeKotlin(String className, String body) throws IOException {
		Path path = projectRoot.resolve(className + ".kt");
		Files.writeString(path, body);
		return ClassContainer.load(path, path.getFileName().toString());
	}

	private boolean contains(Set<ClassContainer> classes, String fileName) {
		return classes.stream().anyMatch(clazz -> clazz.className().equals(fileName));
	}
}

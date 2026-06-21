package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProjectSourcesTest {

	@TempDir
	Path projectRoot;

	@Test
	void loadsOnlySourceFilesOfTheConfiguredLanguage() throws IOException {
		writeFile("A.java", "public class A {}");
		writeFile("B.java", "public class B {}");
		writeFile("notes.txt", "ignore me");
		writeFile("Script.kt", "class Script");

		Set<String> names = classNames(new ProjectSources(Language.JAVA).load(projectRoot));

		assertEquals(Set.of("A.java", "B.java"), names);
	}

	@Test
	void recognisesKotlinSources() throws IOException {
		writeFile("A.java", "public class A {}");
		writeFile("Script.kt", "class Script");

		Set<String> names = classNames(new ProjectSources(Language.KOTLIN).load(projectRoot));

		assertEquals(Set.of("Script.kt"), names);
	}

	@Test
	void readsTheOriginalSourceIntoEachContainer() throws IOException {
		writeFile("A.java", "public class A { int x; }");

		Map<Path, ClassContainer> containers = new ProjectSources(Language.JAVA).load(projectRoot);

		ClassContainer container = containers.get(projectRoot.resolve("A.java"));
		assertTrue(container.originalCode().contains("int x;"));
	}

	@Test
	void emptyProjectYieldsNoSources() {
		assertTrue(new ProjectSources(Language.JAVA).load(projectRoot).isEmpty());
	}

	private void writeFile(String name, String content) throws IOException {
		Files.writeString(projectRoot.resolve(name), content);
	}

	private Set<String> classNames(Map<Path, ClassContainer> containers) {
		return containers.values().stream().map(ClassContainer::className).collect(Collectors.toSet());
	}
}

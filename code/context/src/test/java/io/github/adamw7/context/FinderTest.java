package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class FinderTest {
	protected final static Set<ClassContainer> allContainers = new HashSet<>();
	protected final	 static String currentDir = new File(System.getProperty("user.dir")).getParentFile().getParent() 
			+ "/data/src/main/java/io/github/adamw7/tools/data/source/file/";

	@BeforeAll
	public static void prepareSources() {
		allContainers.add(createContainer(currentDir + toFileName("AbstractFileSource")));
		allContainers.add(createContainer(currentDir + toFileName("CSVDataSource")));
	}

	private static String toFileName(String clazz) {
		return clazz + ".java";
	}

	private static ClassContainer createContainer(String location) {
		File file = new File(location);
		Path path = Path.of(file.getAbsolutePath());
		return new ClassContainer(path, path.getFileName().toString());
	}

	@Test
	void levelOne() {
		ClassContainer root = createContainer(currentDir + toFileName("AbstractFileSource"));
		Set<ClassContainer> classes = new Finder(allContainers).find(root, 1);

		require(classes, toFileName("AbstractFileSource"));
	}

	private void require(Set<ClassContainer> classes, String... fileNames) {
		assertEquals(classes.size(), fileNames.length);
		Set<String> found = lookForFileNames(classes, fileNames);
		for (String fileName : fileNames) {
			assertTrue(found.contains(fileName));
		}
	}

	private Set<String> lookForFileNames(Set<ClassContainer> classes, String... fileNames) {
		Set<String> found = new HashSet<>();

		for (ClassContainer clazz : classes) {
			for (String fileName : fileNames) {
				if (clazz.className().equals(fileName)) {
					found.add(fileName);
				}
			}
		}
		return found;
	}
	
	@Test
	void levelTwo() {
		ClassContainer root = createContainer(currentDir + toFileName("CSVDataSource"));
		
		Set<ClassContainer> classes = new Finder(allContainers).find(root, 2);

		require(classes, toFileName("AbstractFileSource"),
				toFileName("CSVDataSource"));
	}
}

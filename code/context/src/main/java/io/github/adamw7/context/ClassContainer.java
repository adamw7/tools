package io.github.adamw7.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClassContainer {

	private final String className;
	private final String originalCode;

	public ClassContainer(String className, String originalCode) {
		this.className = className;
		this.originalCode = originalCode;
	}

	public static ClassContainer load(Path path, String className) {
		try {
			return new ClassContainer(className, Files.readString(path));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public String originalCode() {
		return originalCode;
	}

	public String className() {
		return className;
	}
}

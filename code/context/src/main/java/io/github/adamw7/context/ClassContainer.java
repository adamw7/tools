package io.github.adamw7.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

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

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ClassContainer container)) {
			return false;
		}
		return className.equals(container.className) && originalCode.equals(container.originalCode);
	}

	@Override
	public int hashCode() {
		return Objects.hash(className, originalCode);
	}
}

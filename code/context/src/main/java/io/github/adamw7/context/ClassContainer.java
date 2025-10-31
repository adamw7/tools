package io.github.adamw7.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClassContainer {
	
	private final Path originalPath;
	private final String className; 
	private final String originalCode;

	public ClassContainer(Path path, String className) {
		this.originalPath = path;
		this.className = className;
		originalCode = readCode();
	}
	

	private String readCode() {
        try {
            return Files.readString(originalPath);
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

package io.github.adamw7.tools.adopt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes the text files an adoption touches, failing with an
 * {@link AdoptionException} rather than a raw {@link IOException} every caller would
 * wrap for itself. Writes and moves create the parent directories first, so a file
 * under a directory the checkout does not carry yet still lands; {@code description} names
 * the file in the failure message.
 */
public final class AdoptionFiles {

	private AdoptionFiles() {
	}

	public static String read(Path file, String description) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new AdoptionException("Could not read " + description + ": " + file, e);
		}
	}

	public static void write(Path file, String content, String description) {
		try {
			createParentDirectories(file);
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new AdoptionException("Could not write " + description + ": " + file, e);
		}
	}

	/**
	 * Moves {@code source} onto {@code target}, replacing whatever is there, after
	 * creating the target's parent directories the way {@link #write} does.
	 */
	public static void move(Path source, Path target, String description) {
		try {
			createParentDirectories(target);
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new AdoptionException("Could not move " + description + " from " + source + " to " + target, e);
		}
	}

	/**
	 * The path is absolutised first so a relative file name — which {@link Path#getParent}
	 * answers {@code null} for, having no parent of its own — still resolves to the
	 * directory it will be written into, and a path that has no parent even then is left
	 * alone rather than dereferenced.
	 */
	private static void createParentDirectories(Path file) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}
}

package io.github.adamw7.tools.adopt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes the text files an adoption touches — the checkout's build
 * script, the generated {@code CLAUDE.md}, the starter assets, the run's JSON
 * report — failing with an {@link AdoptionException} rather than a raw
 * {@link IOException} every caller would have to wrap for itself. Writes create
 * the parent directories first, so an asset under a directory the checkout does
 * not carry yet ({@code .claude/hooks/}) still lands, and the
 * {@code description} names the file in the failure message the way the operator
 * thinks of it.
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
	 * The path is absolutised first so a relative file name — which has no parent
	 * of its own — still resolves to the directory it will be written into.
	 */
	private static void createParentDirectories(Path file) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}
}

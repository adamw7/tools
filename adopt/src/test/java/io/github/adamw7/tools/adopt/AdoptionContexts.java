package io.github.adamw7.tools.adopt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The checkout fixture the step tests share: an {@link AdoptionContext} whose
 * repository directory exists, as it would after {@link
 * io.github.adamw7.tools.adopt.step.CloneStep} has run. Every step from the
 * clone onwards acts on that directory, so each of their tests needs the same
 * two lines before it can assert anything — building them here keeps the tests
 * about the step rather than about its setup.
 */
public final class AdoptionContexts {

	private static final String REPOSITORY_URL = "https://github.com/adamw7/demo.git";

	private AdoptionContexts() {
	}

	/** A context whose checkout directory exists under {@code workspace}, ready for a step to act on. */
	public static AdoptionContext checkedOutIn(Path workspace) throws IOException {
		AdoptionContext context = new AdoptionContext(REPOSITORY_URL, workspace);
		Files.createDirectories(context.repositoryDirectory());
		return context;
	}

	/**
	 * Writes a file into the checkout — a build file for the build-system detection
	 * to find, or a {@code CLAUDE.md} standing in for one {@code claude init}
	 * generated.
	 *
	 * @return the file that was written
	 */
	public static Path write(AdoptionContext context, String relativePath, String content) throws IOException {
		Path file = context.repositoryDirectory().resolve(relativePath);
		Files.createDirectories(file.getParent());
		return Files.writeString(file, content);
	}
}

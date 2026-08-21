package io.github.adamw7.context.okf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Materialises an {@link OkfBundle} as a directory tree on disk. The Open
 * Knowledge Format is deliberately "just files", so a bundle needs no packaging
 * beyond writing each document at its bundle-relative path — the result can then
 * be committed to a git repository, archived as a tarball or mounted straight
 * into an agent's file system.
 */
public class OkfBundleWriter {

	private static final Logger log = LogManager.getLogger(OkfBundleWriter.class.getName());

	/**
	 * Writes every document of the bundle beneath {@code target}, creating the
	 * directories each one needs.
	 *
	 * @return the bundle root that was written to
	 */
	public Path write(OkfBundle bundle, Path target) {
		bundle.documents().forEach(document -> writeDocument(document, target));
		log.info("Wrote an OKF v{} bundle of {} documents to {}", OkfBundle.VERSION, bundle.documents().size(),
				target);
		return target;
	}

	/**
	 * The path is absolutised before its parent is taken, so a document written under an
	 * empty target — which {@link Path#getParent} answers {@code null} for, the resolved
	 * path being a bare file name — still names the directory it lands in instead of
	 * being dereferenced as {@code null}.
	 */
	private void writeDocument(OkfDocument document, Path target) {
		Path file = target.resolve(document.path());
		try {
			createParentDirectories(file);
			Files.writeString(file, document.content(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write the OKF document " + file, e);
		}
	}

	private static void createParentDirectories(Path file) throws IOException {
		Path directory = file.toAbsolutePath().getParent();
		if (directory != null) {
			Files.createDirectories(directory);
		}
	}
}

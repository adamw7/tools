package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * Writes one starter asset file into a checkout: the file's repository-relative
 * path, its content, and whether it must be executable (for hook scripts).
 * Installation is deliberately conservative: a file that already exists is never
 * overwritten — the project's own version always wins over the starter content —
 * so re-running the adoption, or adopting a repository that already configured
 * Claude Code, leaves the checkout untouched.
 */
public class AssetInstaller {

	private static final Logger log = LogManager.getLogger(AssetInstaller.class);

	private final String relativePath;
	private final String content;
	private final boolean executable;

	public AssetInstaller(String relativePath, String content) {
		this(relativePath, content, false);
	}

	public AssetInstaller(String relativePath, String content, boolean executable) {
		this.relativePath = relativePath;
		this.content = content;
		this.executable = executable;
	}

	public String relativePath() {
		return relativePath;
	}

	/**
	 * @return {@code true} when the asset was written, {@code false} when the
	 *         checkout already carried a file at its path and was left unchanged.
	 */
	public boolean install(Path repositoryDirectory) {
		Path file = repositoryDirectory.resolve(relativePath);
		if (Files.exists(file)) {
			return false;
		}
		write(file);
		return true;
	}

	private void write(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
		} catch (IOException e) {
			throw new AdoptionException("Could not write asset file: " + file, e);
		}
		markExecutable(file);
	}

	private void markExecutable(Path file) {
		if (executable && !file.toFile().setExecutable(true, false)) {
			log.warn("Could not mark {} executable; the filesystem does not support it", file);
		}
	}
}

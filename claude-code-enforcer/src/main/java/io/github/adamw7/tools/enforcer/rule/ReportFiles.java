package io.github.adamw7.tools.enforcer.rule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The one place the enforcer writes a file. The HTML report, the index linking the
 * reports and a recorded baseline all go through here, each to where a configured
 * parameter or property pointed it, so the architecture test can hold every other
 * class to reading alone.
 */
final class ReportFiles {

	private ReportFiles() {
	}

	/**
	 * Writes {@code content} to {@code file} as UTF-8, replacing what was there. Missing
	 * parent directories are created first: a report under {@code target/} is written at
	 * {@code validate}, before any plugin created it. A path with no parent to create
	 * &mdash; which {@link Path#getParent} answers {@code null} for at a filesystem root
	 * &mdash; simply has nothing to make before the write.
	 */
	static void write(Path file, String content) throws IOException {
		Path path = file.toAbsolutePath();
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}
}

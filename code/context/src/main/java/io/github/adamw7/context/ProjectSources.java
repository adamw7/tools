package io.github.adamw7.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads the source files of a Java or Kotlin project from disk into
 * {@link ClassContainer}s, keyed by their {@link Path}. A source file is any
 * regular file whose name ends with the configured {@link Language}'s extension.
 * Centralising the rules for recognising and reading sources here keeps the
 * {@link io.github.adamw7.context.tree.ProjectTreeBuilder} and the MCP tools that
 * expose the finders from each re-implementing the same project walk.
 */
public class ProjectSources {

	private final Language language;

	public ProjectSources(Language language) {
		this.language = language;
	}

	public Map<Path, ClassContainer> load(Path root) {
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(this::isSourceFile)
					.collect(Collectors.toMap(path -> path, this::toContainer));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private boolean isSourceFile(Path path) {
		return Files.isRegularFile(path) && path.getFileName().toString().endsWith(language.extension());
	}

	private ClassContainer toContainer(Path path) {
		return ClassContainer.load(path, path.getFileName().toString());
	}
}

package io.github.adamw7.context.tree;

import io.github.adamw7.context.ClassContainer;
import io.github.adamw7.context.Context;
import io.github.adamw7.context.ContextFactory;
import io.github.adamw7.context.Finder;
import io.github.adamw7.context.Language;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a {@link ProjectTreeNode} tree from a Java or Kotlin project on disk.
 * The tree mirrors the project's folders and files; every source file (a
 * {@code .java} or {@code .kt} file, depending on the configured
 * {@link Language}) is enriched with the classes it depends on, resolved by a
 * {@link Context} over all the project's sources. The result is a
 * ready-to-serialise context for gen-AI agents working with Java or Kotlin code.
 */
public class ProjectTreeBuilder {

	private final ContextFactory contextFactory;
	private final Language language;
	private final int depth;

	public ProjectTreeBuilder(int depth) {
		this(Language.JAVA, depth);
	}

	public ProjectTreeBuilder(Language language, int depth) {
		this(containers -> new Finder(containers, language), language, depth);
	}

	public ProjectTreeBuilder(ContextFactory contextFactory, int depth) {
		this(contextFactory, Language.JAVA, depth);
	}

	public ProjectTreeBuilder(ContextFactory contextFactory, Language language, int depth) {
		this.contextFactory = contextFactory;
		this.language = language;
		this.depth = depth;
	}

	public ProjectTreeNode build(Path root) {
		Map<Path, ClassContainer> containersByPath = loadContainers(root);
		Context context = contextFactory.create(new HashSet<>(containersByPath.values()));
		return buildNode(root, context, containersByPath);
	}

	private Map<Path, ClassContainer> loadContainers(Path root) {
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

	private ProjectTreeNode buildNode(Path path, Context context, Map<Path, ClassContainer> containers) {
		if (Files.isDirectory(path)) {
			return buildDirectory(path, context, containers);
		}
		return buildFile(path, context, containers);
	}

	private ProjectTreeNode buildDirectory(Path path, Context context, Map<Path, ClassContainer> containers) {
		ProjectTreeNode node = ProjectTreeNode.directory(path);
		listChildren(path).forEach(child -> node.addChild(buildNode(child, context, containers)));
		return node;
	}

	private ProjectTreeNode buildFile(Path path, Context context, Map<Path, ClassContainer> containers) {
		ProjectTreeNode node = ProjectTreeNode.file(path);
		addDependencies(node, path, context, containers);
		return node;
	}

	private void addDependencies(ProjectTreeNode node, Path path, Context context,
			Map<Path, ClassContainer> containers) {
		ClassContainer container = containers.get(path);
		if (container == null) {
			return;
		}
		context.find(container, depth).stream()
				.map(ClassContainer::className)
				.sorted()
				.forEach(node::addDependency);
	}

	private List<Path> listChildren(Path directory) {
		try (Stream<Path> entries = Files.list(directory)) {
			return entries.sorted(childOrdering()).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Comparator<Path> childOrdering() {
		return Comparator.comparing((Path path) -> Files.isDirectory(path) ? 0 : 1)
				.thenComparing(path -> path.getFileName().toString());
	}
}

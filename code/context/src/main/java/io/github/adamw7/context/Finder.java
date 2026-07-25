package io.github.adamw7.context;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the classes a source file depends on by scanning its text for class
 * references. The depth-bounded, breadth-first traversal of the dependency graph
 * is inherited from {@link AbstractFinder}; this class supplies only the direct
 * resolution step.
 *
 * <p>Resolution is by simple file name (a referenced {@code Foo} resolves to a
 * {@code Foo} source file of the configured {@link Language}). The containers are
 * indexed by file name once at construction, so each reference resolves in
 * constant time rather than by scanning every container. Comments, string and
 * character literals are stripped before matching so that class names mentioned
 * there are not reported as dependencies. Two source files sharing the same
 * simple name in different packages still cannot be told apart — that needs
 * package-aware resolution, which this name-based finder does not attempt (see
 * {@link PackageAwareFinder}); the first one encountered while indexing wins.
 */
public class Finder extends AbstractFinder {

	private final Map<String, ClassContainer> containersByName;
	private final Language language;

	public Finder(Set<ClassContainer> allContainers) {
		this(allContainers, Language.JAVA);
	}

	public Finder(Set<ClassContainer> allContainers, Language language) {
		this.language = language;
		this.containersByName = indexByName(allContainers);
	}

	private Map<String, ClassContainer> indexByName(Set<ClassContainer> allContainers) {
		return allContainers.stream().collect(Collectors.toMap(
				ClassContainer::className,
				container -> container,
				(first, second) -> first));
	}

	@Override
	protected Set<ClassContainer> findDirectDependencies(ClassContainer source) {
		return resolveReferences(source, stripCommentsAndLiterals(source.originalCode()), this::findContainer);
	}

	private ClassContainer findContainer(String className) {
		return containersByName.get(className + language.extension());
	}
}

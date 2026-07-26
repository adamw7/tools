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
 * {@code Foo} source file of the configured {@link Language}), with the containers
 * indexed by file name once at construction so each reference resolves in constant
 * time. Comments, string and character literals are stripped before matching, so
 * class names mentioned there are not reported as dependencies. Two source files
 * sharing a simple name in different packages cannot be told apart — the first one
 * indexed wins; that needs the package-aware {@link PackageAwareFinder}.
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

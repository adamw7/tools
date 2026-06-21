package io.github.adamw7.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves the classes a source file depends on by scanning its text for class
 * references. Traversal is a breadth-first expansion of the dependency graph
 * bounded by {@code depth}: every visited class is recorded once, so cycles
 * terminate and no node is expanded twice. The root itself is never reported as
 * one of its own dependencies.
 *
 * <p>Resolution is by simple file name (a referenced {@code Foo} resolves to a
 * {@code Foo} source file of the configured {@link Language}). The containers are
 * indexed by file name once at construction, so each reference resolves in
 * constant time rather than by scanning every container. Comments, string and
 * character literals are stripped before matching so that class names mentioned
 * there are not reported as dependencies. Two source files sharing the same
 * simple name in different packages still cannot be told apart — that needs
 * package-aware resolution, which this name-based finder does not attempt; the
 * first one encountered while indexing wins.
 */
public class Finder implements Context {

	private static final Logger log = LogManager.getLogger(Finder.class.getName());

	private static final Pattern CLASS_REFERENCE =
			Pattern.compile("\\b[A-Z][A-Za-z0-9_]*(\\.[A-Z][A-Za-z0-9_]*)*\\b");

	private static final Pattern COMMENTS_AND_LITERALS = Pattern.compile(
			"\"\"\".*?\"\"\""              // Kotlin triple-quoted string
			+ "|//[^\\n]*"                 // line comment
			+ "|/\\*.*?\\*/"               // block comment
			+ "|\"(?:\\\\.|[^\"\\\\])*\""  // string literal
			+ "|'(?:\\\\.|[^'\\\\])*'",    // character literal
			Pattern.DOTALL);

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
	public Set<ClassContainer> find(ClassContainer root, int depth) {
		requirePositiveDepth(depth);
		Set<ClassContainer> dependencies = new LinkedHashSet<>();
		Set<ClassContainer> visited = new HashSet<>();
		visited.add(root);
		expand(root, depth, dependencies, visited);
		return dependencies;
	}

	private void requirePositiveDepth(int depth) {
		if (depth <= 0) {
			throw new IllegalArgumentException("Depth must be positive and received: " + depth);
		}
	}

	private void expand(ClassContainer root, int depth, Set<ClassContainer> dependencies,
			Set<ClassContainer> visited) {
		Set<ClassContainer> frontier = Set.of(root);
		for (int level = 0; level < depth && !frontier.isEmpty(); level++) {
			frontier = nextLevel(frontier, dependencies, visited);
		}
	}

	private Set<ClassContainer> nextLevel(Set<ClassContainer> frontier,
			Set<ClassContainer> dependencies, Set<ClassContainer> visited) {
		Set<ClassContainer> next = new LinkedHashSet<>();
		for (ClassContainer current : frontier) {
			addNewDependencies(current, dependencies, visited, next);
		}
		return next;
	}

	private void addNewDependencies(ClassContainer current, Set<ClassContainer> dependencies,
			Set<ClassContainer> visited, Set<ClassContainer> next) {
		for (ClassContainer dependency : findDirectDependencies(current)) {
			if (visited.add(dependency)) {
				dependencies.add(dependency);
				next.add(dependency);
			}
		}
	}

	private Set<ClassContainer> findDirectDependencies(ClassContainer source) {
		Set<ClassContainer> dependencies = new LinkedHashSet<>();
		Matcher matcher = CLASS_REFERENCE.matcher(stripCommentsAndLiterals(source.originalCode()));
		while (matcher.find()) {
			addResolved(matcher.group(), source, dependencies);
		}
		return dependencies;
	}

	private void addResolved(String className, ClassContainer source, Set<ClassContainer> dependencies) {
		ClassContainer container = findContainer(className);
		if (container != null) {
			log.info("Found {} used in {}", container.className(), source.className());
			dependencies.add(container);
		}
	}

	private String stripCommentsAndLiterals(String code) {
		return COMMENTS_AND_LITERALS.matcher(code).replaceAll(" ");
	}

	private ClassContainer findContainer(String className) {
		return containersByName.get(className + language.extension());
	}
}

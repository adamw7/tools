package io.github.adamw7.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Shared skeleton for the regex-based {@link Context} finders. It owns the
 * depth-bounded, breadth-first traversal of the dependency graph: starting from
 * the root, each level's direct dependencies become the next level's frontier,
 * every class is visited once so cycles terminate, and the root is never reported
 * as one of its own dependencies. Subclasses supply only
 * {@link #findDirectDependencies(ClassContainer)} — how a single source file's
 * class references resolve to concrete containers — which is the one concern in
 * which name-based and package-aware resolution differ.
 */
public abstract class AbstractFinder implements Context {

	private static final Logger log = LogManager.getLogger(AbstractFinder.class.getName());

	protected static final Pattern CLASS_REFERENCE =
			Pattern.compile("\\b[A-Z][A-Za-z0-9_]*(\\.[A-Z][A-Za-z0-9_]*)*\\b");

	private static final Pattern COMMENTS_AND_LITERALS = Pattern.compile(
			"\"\"\".*?\"\"\""              // triple-quoted string (Kotlin/Scala)
			+ "|//[^\\n]*"                 // line comment
			+ "|/\\*.*?\\*/"               // block comment
			+ "|\"(?:\\\\.|[^\"\\\\])*\""  // string literal
			+ "|'(?:\\\\.|[^'\\\\])*'",    // character literal
			Pattern.DOTALL);

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

	/**
	 * Resolves the classes directly referenced by {@code source}. The traversal
	 * that turns these into a depth-bounded transitive closure is handled by this
	 * base class.
	 */
	protected abstract Set<ClassContainer> findDirectDependencies(ClassContainer source);

	/**
	 * Runs {@code resolver} over every class reference in {@code strippedCode},
	 * keeping the ones that resolve to a container in encounter order. Both finders
	 * scan identically and differ only in how a single reference resolves, which is
	 * exactly what {@code resolver} supplies.
	 */
	protected Set<ClassContainer> resolveReferences(ClassContainer source, String strippedCode,
			Function<String, ClassContainer> resolver) {
		Set<ClassContainer> dependencies = new LinkedHashSet<>();
		CLASS_REFERENCE.matcher(strippedCode).results()
				.map(result -> resolver.apply(result.group()))
				.filter(Objects::nonNull)
				.forEach(container -> addResolved(container, source, dependencies));
		return dependencies;
	}

	private void addResolved(ClassContainer container, ClassContainer source, Set<ClassContainer> dependencies) {
		log.info("Resolved {} used in {}", container.className(), source.className());
		dependencies.add(container);
	}

	protected String stripCommentsAndLiterals(String code) {
		return COMMENTS_AND_LITERALS.matcher(code).replaceAll(" ");
	}
}

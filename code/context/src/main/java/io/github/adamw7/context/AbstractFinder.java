package io.github.adamw7.context;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
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

	protected String stripCommentsAndLiterals(String code) {
		return COMMENTS_AND_LITERALS.matcher(code).replaceAll(" ");
	}
}

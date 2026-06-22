package io.github.adamw7.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A {@link Context} that resolves dependencies using each source's {@code package}
 * declaration and {@code import} statements, so two classes that share a simple
 * name in different packages can be told apart — the gap the name-based
 * {@link Finder} explicitly leaves open. A referenced {@code Foo} resolves, in
 * order of preference, to: an explicitly imported {@code a.b.Foo}; a {@code Foo}
 * in the referencing file's own package; a {@code Foo} reachable through a
 * wildcard import {@code a.b.*}; or, only when exactly one {@code Foo} exists in
 * the whole project, that sole candidate. An ambiguous reference with no import to
 * disambiguate it is left unresolved rather than guessed.
 *
 * <p>Like {@link Finder}, traversal is a depth-bounded breadth-first expansion in
 * which every class is visited once, so cycles terminate and the root is never
 * reported as its own dependency. Comments and string or character literals are
 * stripped before matching. The package/import grammar this relies on
 * ({@code package a.b;} and {@code import a.b.C;}) is shared by Java, Kotlin and
 * Scala, so it serves every {@link Language} the finder supports.
 */
public class PackageAwareFinder implements Context {

	private static final Logger log = LogManager.getLogger(PackageAwareFinder.class.getName());

	private static final Pattern CLASS_REFERENCE =
			Pattern.compile("\\b[A-Z][A-Za-z0-9_]*(\\.[A-Z][A-Za-z0-9_]*)*\\b");

	private static final Pattern COMMENTS_AND_LITERALS = Pattern.compile(
			"\"\"\".*?\"\"\""              // triple-quoted string
			+ "|//[^\\n]*"                 // line comment
			+ "|/\\*.*?\\*/"               // block comment
			+ "|\"(?:\\\\.|[^\"\\\\])*\""  // string literal
			+ "|'(?:\\\\.|[^'\\\\])*'",    // character literal
			Pattern.DOTALL);

	private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)");

	private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(\\w+(?:\\.\\w+)*(?:\\.\\*)?)");

	private final Language language;
	private final Map<String, ClassContainer> containersByFqn;
	private final Map<String, List<ClassContainer>> containersBySimpleName;

	public PackageAwareFinder(Set<ClassContainer> allContainers) {
		this(allContainers, Language.JAVA);
	}

	public PackageAwareFinder(Set<ClassContainer> allContainers, Language language) {
		this.language = language;
		this.containersByFqn = indexByFqn(allContainers);
		this.containersBySimpleName = indexBySimpleName(allContainers);
	}

	private Map<String, ClassContainer> indexByFqn(Set<ClassContainer> allContainers) {
		return allContainers.stream().collect(Collectors.toMap(
				this::fullyQualifiedName,
				container -> container,
				(first, second) -> first));
	}

	private Map<String, List<ClassContainer>> indexBySimpleName(Set<ClassContainer> allContainers) {
		return allContainers.stream().collect(Collectors.groupingBy(this::simpleName));
	}

	private String fullyQualifiedName(ClassContainer container) {
		return qualify(packageOf(container.originalCode()), simpleName(container));
	}

	private String simpleName(ClassContainer container) {
		String fileName = container.className();
		return fileName.substring(0, fileName.length() - language.extension().length());
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
		String code = stripCommentsAndLiterals(source.originalCode());
		ResolutionScope scope = scopeOf(code);
		Set<ClassContainer> dependencies = new LinkedHashSet<>();
		Matcher matcher = CLASS_REFERENCE.matcher(code);
		while (matcher.find()) {
			addResolved(matcher.group(), scope, source, dependencies);
		}
		return dependencies;
	}

	private void addResolved(String reference, ResolutionScope scope, ClassContainer source,
			Set<ClassContainer> dependencies) {
		ClassContainer container = resolve(outerSimpleName(reference), scope);
		if (container != null) {
			log.info("Resolved {} used in {}", container.className(), source.className());
			dependencies.add(container);
		}
	}

	private String outerSimpleName(String reference) {
		int dot = reference.indexOf('.');
		return dot < 0 ? reference : reference.substring(0, dot);
	}

	private ClassContainer resolve(String simpleName, ResolutionScope scope) {
		ClassContainer imported = resolveImported(simpleName, scope);
		if (imported != null) {
			return imported;
		}
		ClassContainer samePackage = containersByFqn.get(qualify(scope.packageName(), simpleName));
		if (samePackage != null) {
			return samePackage;
		}
		ClassContainer wildcard = resolveWildcard(simpleName, scope);
		if (wildcard != null) {
			return wildcard;
		}
		return resolveUniqueSimpleName(simpleName);
	}

	private ClassContainer resolveImported(String simpleName, ResolutionScope scope) {
		String fqn = scope.explicitImports().get(simpleName);
		return fqn == null ? null : containersByFqn.get(fqn);
	}

	private ClassContainer resolveWildcard(String simpleName, ResolutionScope scope) {
		return scope.wildcardPackages().stream()
				.map(packageName -> containersByFqn.get(qualify(packageName, simpleName)))
				.filter(container -> container != null)
				.findFirst()
				.orElse(null);
	}

	private ClassContainer resolveUniqueSimpleName(String simpleName) {
		List<ClassContainer> candidates = containersBySimpleName.getOrDefault(simpleName, List.of());
		return candidates.size() == 1 ? candidates.getFirst() : null;
	}

	private String qualify(String packageName, String simpleName) {
		return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
	}

	private ResolutionScope scopeOf(String code) {
		return new ResolutionScope(packageOf(code), explicitImports(code), wildcardPackages(code));
	}

	private String packageOf(String code) {
		Matcher matcher = PACKAGE.matcher(stripCommentsAndLiterals(code));
		return matcher.find() ? matcher.group(1) : "";
	}

	private Map<String, String> explicitImports(String code) {
		Matcher matcher = IMPORT.matcher(code);
		Map<String, String> imports = new HashMap<>();
		while (matcher.find()) {
			recordExplicitImport(matcher.group(1), imports);
		}
		return imports;
	}

	private void recordExplicitImport(String imported, Map<String, String> imports) {
		if (!imported.endsWith(".*")) {
			imports.put(lastSegment(imported), imported);
		}
	}

	private List<String> wildcardPackages(String code) {
		Matcher matcher = IMPORT.matcher(code);
		List<String> packages = new ArrayList<>();
		while (matcher.find()) {
			recordWildcard(matcher.group(1), packages);
		}
		return packages;
	}

	private void recordWildcard(String imported, List<String> packages) {
		if (imported.endsWith(".*")) {
			packages.add(imported.substring(0, imported.length() - 2));
		}
	}

	private String lastSegment(String dotted) {
		int dot = dotted.lastIndexOf('.');
		return dot < 0 ? dotted : dotted.substring(dot + 1);
	}

	private String stripCommentsAndLiterals(String code) {
		return COMMENTS_AND_LITERALS.matcher(code).replaceAll(" ");
	}

	/**
	 * The package, explicit imports (simple name to fully-qualified name) and
	 * wildcard-imported packages of a single source file, computed once and reused
	 * to resolve every class reference within that file.
	 */
	private record ResolutionScope(String packageName, Map<String, String> explicitImports,
			List<String> wildcardPackages) {
	}
}

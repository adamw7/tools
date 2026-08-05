package io.github.adamw7.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link Context} that resolves dependencies using each source's {@code package}
 * declaration and {@code import} statements, so two classes that share a simple
 * name in different packages can be told apart — the gap the name-based
 * {@link Finder} leaves open. A referenced {@code Foo} resolves, in order of
 * preference, to an explicitly imported {@code a.b.Foo}, a {@code Foo} in the
 * referencing file's own package, one reachable through a wildcard import
 * {@code a.b.*}, or — only when exactly one {@code Foo} exists in the whole
 * project — that sole candidate. An ambiguous reference with no import to
 * disambiguate it is left unresolved rather than guessed.
 *
 * <p>Traversal is the depth-bounded breadth-first expansion of
 * {@link AbstractFinder}, and comments and string or character literals are
 * stripped before matching. The package/import grammar this relies on is shared by
 * Java, Kotlin and Scala, so it serves every {@link Language} the finder supports.
 */
public class PackageAwareFinder extends AbstractFinder {

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
		return qualify(packageOf(stripCommentsAndLiterals(container.originalCode())), simpleName(container));
	}

	private String simpleName(ClassContainer container) {
		String fileName = container.className();
		return fileName.substring(0, fileName.length() - language.extension().length());
	}

	@Override
	protected Set<ClassContainer> findDirectDependencies(ClassContainer source) {
		String code = stripCommentsAndLiterals(source.originalCode());
		ResolutionScope scope = scopeOf(code);
		return resolveReferences(source, code, reference -> resolve(outerSimpleName(reference), scope));
	}

	private String outerSimpleName(String reference) {
		int dot = reference.indexOf('.');
		return dot < 0 ? reference : reference.substring(0, dot);
	}

	/** The candidates in order of preference; the first that resolves wins, and none resolving leaves the reference unresolved. */
	private ClassContainer resolve(String simpleName, ResolutionScope scope) {
		return Stream.<Supplier<ClassContainer>>of(
						() -> resolveImported(simpleName, scope),
						() -> containersByFqn.get(qualify(scope.packageName(), simpleName)),
						() -> resolveWildcard(simpleName, scope),
						() -> resolveUniqueSimpleName(simpleName))
				.map(Supplier::get)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	private ClassContainer resolveImported(String simpleName, ResolutionScope scope) {
		String fqn = scope.explicitImports().get(simpleName);
		return fqn == null ? null : containersByFqn.get(fqn);
	}

	private ClassContainer resolveWildcard(String simpleName, ResolutionScope scope) {
		return scope.wildcardPackages().stream()
				.map(packageName -> containersByFqn.get(qualify(packageName, simpleName)))
				.filter(Objects::nonNull)
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

	private ResolutionScope scopeOf(String strippedCode) {
		List<String> imports = IMPORT.matcher(strippedCode).results().map(result -> result.group(1)).toList();
		return new ResolutionScope(packageOf(strippedCode), explicitImports(imports), wildcardPackages(imports));
	}

	private String packageOf(String strippedCode) {
		Matcher matcher = PACKAGE.matcher(strippedCode);
		return matcher.find() ? matcher.group(1) : "";
	}

	private Map<String, String> explicitImports(List<String> imports) {
		return imports.stream()
				.filter(imported -> !isWildcard(imported))
				.collect(Collectors.toMap(this::lastSegment, imported -> imported, (first, second) -> second));
	}

	private List<String> wildcardPackages(List<String> imports) {
		return imports.stream()
				.filter(this::isWildcard)
				.map(imported -> imported.substring(0, imported.length() - 2))
				.toList();
	}

	private boolean isWildcard(String imported) {
		return imported.endsWith(".*");
	}

	private String lastSegment(String dotted) {
		int dot = dotted.lastIndexOf('.');
		return dot < 0 ? dotted : dotted.substring(dot + 1);
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

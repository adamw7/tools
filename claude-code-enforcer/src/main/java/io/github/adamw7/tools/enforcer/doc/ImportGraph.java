package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.github.adamw7.tools.enforcer.rule.ProjectFiles;
import io.github.adamw7.tools.enforcer.text.MarkdownDocument;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * The {@code @path} memory imports reachable from a root document, together with
 * the fewest hops each target sits from that root.
 * <p>
 * The hop count is what makes {@link MemoryImportsRule} decide the same way every
 * run. Claude Code loads a file that any chain reaches within its hop limit, so
 * whether a target is loaded depends on its <em>shortest</em> chain, not on
 * whichever chain a traversal happens to walk first. The graph is therefore built
 * breadth-first, so the first time a file is reached is by its shortest chain, and
 * the rule reads that answer instead of counting hops as it recurses.
 * <p>
 * Imports are recognised the way Claude Code evaluates them: an {@code @} preceded
 * by start-of-line or whitespace and followed by a path — one carrying a directory
 * separator, an extension, or both — outside code, HTML comments and inline code
 * spans alike, so neither {@code `@claude`} nor a bare {@code @claude} in prose is
 * an import, an import shown as a sample (fenced or indented) is one the document
 * illustrates rather than makes, and one an author commented out is one it no
 * longer makes. A
 * home-relative import ({@code @~/...}) names machine-specific state a build
 * cannot see and is skipped, as is any import the {@code ignored} predicate
 * accepts. Only a <em>leading</em> {@code ~} makes an import home-relative: one
 * inside a path is an ordinary character, which is how Windows spells a short
 * (8.3) name such as {@code RUNNER~1}. A file
 * that cannot be read as text is treated as a leaf rather than a failure, because
 * an imported file may be any format.
 */
final class ImportGraph {

	/** One {@code @path} import: the path as its author wrote it, and the file it resolves to. */
	record Reference(String text, File target) {
	}

	private static final Pattern IMPORT = Pattern.compile("(?<=^|\\s)@([A-Za-z0-9_./~-]+)");
	private static final Pattern TRAILING_DOTS = Pattern.compile("\\.+$");
	private static final char PATH_SEPARATOR = '/';
	private static final char EXTENSION_SEPARATOR = '.';
	private static final String HOME_PREFIX = "~";

	private final Map<Path, List<Reference>> references = new LinkedHashMap<>();
	private final Map<Path, Integer> hops = new LinkedHashMap<>();
	private final Predicate<String> ignored;
	private final Consumer<String> unreadable;

	private ImportGraph(Predicate<String> ignored, Consumer<String> unreadable) {
		this.ignored = ignored;
		this.unreadable = unreadable;
	}

	/**
	 * Walks every import reachable from {@code root}. Imports the {@code ignored}
	 * predicate accepts are left out of the graph entirely, so they neither carry
	 * a hop count nor pull their target in. {@code unreadable} receives a message
	 * for each import target that could not be read as text.
	 */
	static ImportGraph from(File root, Predicate<String> ignored, Consumer<String> unreadable) {
		ImportGraph graph = new ImportGraph(ignored, unreadable);
		graph.explore(root);
		return graph;
	}

	/** The imports {@code file} declares, in document order, or none when it was never reached. */
	List<Reference> importsOf(File file) {
		return references.getOrDefault(ProjectFiles.normalized(file), List.of());
	}

	/**
	 * The fewest hops from the root to {@code file}, or {@link Integer#MAX_VALUE}
	 * when no chain of imports reaches it.
	 */
	int hopsTo(File file) {
		return hops.getOrDefault(ProjectFiles.normalized(file), Integer.MAX_VALUE);
	}

	/** Breadth-first, so a file is first reached by its shortest chain of imports. */
	private void explore(File root) {
		Deque<File> queue = new ArrayDeque<>();
		hops.put(ProjectFiles.normalized(root), 0);
		queue.add(root);
		while (!queue.isEmpty()) {
			expand(queue.poll(), queue);
		}
	}

	private void expand(File file, Deque<File> queue) {
		Path path = ProjectFiles.normalized(file);
		List<Reference> found = referencesIn(file);
		references.put(path, found);
		int depth = hops.get(path);
		for (Reference reference : found) {
			enqueue(reference, depth + 1, queue);
		}
	}

	/** A target is queued once, at the first — and so shortest — depth it is reached by. */
	private void enqueue(Reference reference, int depth, Deque<File> queue) {
		Path path = ProjectFiles.normalized(reference.target());
		if (reference.target().isFile() && !hops.containsKey(path)) {
			hops.put(path, depth);
			queue.add(reference.target());
		}
	}

	private List<Reference> referencesIn(File file) {
		MarkdownDocument document = MarkdownDocument.parse(readSafely(file));
		return IntStream.range(0, document.lineCount())
				.filter(index -> !document.isInsideCode(index) && !document.isInsideComment(index))
				.mapToObj(document::line)
				.flatMap(line -> lineReferences(file, line))
				.toList();
	}

	private Stream<Reference> lineReferences(File file, String line) {
		return IMPORT.matcher(MarkdownText.withoutCodeSpans(line))
				.results()
				.map(match -> withoutTrailingDots(match.group(1)))
				.filter(ImportGraph::isPath)
				.filter(imported -> !isHomeRelative(imported))
				.filter(imported -> !ignored.test(imported))
				.map(imported -> new Reference(imported, resolve(file, imported)));
	}

	/** Drops sentence punctuation, so "see @docs/setup.md." imports {@code docs/setup.md}. */
	private String withoutTrailingDots(String imported) {
		return TRAILING_DOTS.matcher(imported).replaceAll("");
	}

	/**
	 * True when the token names a path rather than a bare word: it carries a
	 * directory separator, an extension, or both. An import is a path — Claude Code
	 * loads a file by it — so {@code @docs/setup} and {@code @AGENTS.md} are imports
	 * while the {@code @claude} of a mention workflow and the {@code @adamw7} of a
	 * GitHub handle are prose. Reading those as imports failed the build over files
	 * the author never meant to name, which no amount of backticking their every
	 * occurrence could be relied on to prevent.
	 */
	private static boolean isPath(String imported) {
		return imported.indexOf(PATH_SEPARATOR) >= 0 || imported.indexOf(EXTENSION_SEPARATOR) >= 0;
	}

	/**
	 * True when the import starts at the importing user's home directory, which is
	 * machine-specific state a build cannot see. Only the leading position counts:
	 * a {@code ~} further along is an ordinary path character — {@code RUNNER~1} is
	 * how Windows shortens a profile name, and an import spelling an absolute path
	 * on such a machine has to carry it.
	 */
	private static boolean isHomeRelative(String imported) {
		return imported.startsWith(HOME_PREFIX);
	}

	private File resolve(File file, String imported) {
		if (imported.startsWith("/")) {
			return rootedAt(file, imported);
		}
		return new File(file.getParentFile(), imported);
	}

	/**
	 * A leading slash means the path starts at a file system root rather than at
	 * the importing file. Which root that is comes from the importing file, so a
	 * document resolves the same way wherever the build was launched from — on a
	 * single-rooted file system the two are the same, but on Windows
	 * {@code new File("/docs.md")} would pick whichever drive the build happened
	 * to be started on.
	 */
	private File rootedAt(File file, String imported) {
		return ProjectFiles.normalized(file).getRoot().resolve(imported.substring(1)).toFile();
	}

	/**
	 * The file's content, or empty when it cannot be read as text. An imported
	 * file may be any format, so a binary target is treated as a leaf rather than
	 * a failure — its existence is verified by the rule, not here.
	 */
	private String readSafely(File file) {
		try {
			return Files.readString(file.toPath());
		} catch (IOException e) {
			unreadable.accept("Skipping unreadable import target " + file + ": " + e.getMessage());
			return "";
		}
	}
}

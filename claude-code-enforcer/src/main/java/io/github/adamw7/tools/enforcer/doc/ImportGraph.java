package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.adamw7.tools.enforcer.text.MarkdownDocument;

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
 * by start-of-line or whitespace and followed by a path, outside fenced code
 * blocks and inline code spans, so {@code `@claude`} in prose is not an import. A
 * home-relative import ({@code @~/...}) does not match the path syntax at all and
 * so is skipped, as is any import the {@code ignored} predicate accepts. A file
 * that cannot be read as text is treated as a leaf rather than a failure, because
 * an imported file may be any format.
 */
final class ImportGraph {

	/** One {@code @path} import: the path as its author wrote it, and the file it resolves to. */
	record Reference(String text, File target) {
	}

	private static final Pattern IMPORT = Pattern.compile("(?<=^|\\s)@([A-Za-z0-9_./-]+)");
	private static final Pattern CODE_SPAN = Pattern.compile("`[^`]*`");
	private static final Pattern TRAILING_DOTS = Pattern.compile("\\.+$");

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
		return references.getOrDefault(normalized(file), List.of());
	}

	/**
	 * The fewest hops from the root to {@code file}, or {@link Integer#MAX_VALUE}
	 * when no chain of imports reaches it.
	 */
	int hopsTo(File file) {
		return hops.getOrDefault(normalized(file), Integer.MAX_VALUE);
	}

	/** The absolute, symlink-free-of-dot-segments path a file is keyed by. */
	static Path normalized(File file) {
		return file.toPath().toAbsolutePath().normalize();
	}

	/** Breadth-first, so a file is first reached by its shortest chain of imports. */
	private void explore(File root) {
		Deque<File> queue = new ArrayDeque<>();
		hops.put(normalized(root), 0);
		queue.add(root);
		while (!queue.isEmpty()) {
			expand(queue.poll(), queue);
		}
	}

	private void expand(File file, Deque<File> queue) {
		Path path = normalized(file);
		List<Reference> found = referencesIn(file);
		references.put(path, found);
		int depth = hops.get(path);
		for (Reference reference : found) {
			enqueue(reference, depth + 1, queue);
		}
	}

	/** A target is queued once, at the first — and so shortest — depth it is reached by. */
	private void enqueue(Reference reference, int depth, Deque<File> queue) {
		Path path = normalized(reference.target());
		if (reference.target().isFile() && !hops.containsKey(path)) {
			hops.put(path, depth);
			queue.add(reference.target());
		}
	}

	private List<Reference> referencesIn(File file) {
		MarkdownDocument document = MarkdownDocument.parse(readSafely(file));
		List<Reference> found = new ArrayList<>();
		for (int i = 0; i < document.lineCount(); i++) {
			collectLineReferences(file, document, i, found);
		}
		return found;
	}

	private void collectLineReferences(File file, MarkdownDocument document, int index, List<Reference> found) {
		if (document.isInsideFence(index)) {
			return;
		}
		Matcher matcher = IMPORT.matcher(CODE_SPAN.matcher(document.line(index)).replaceAll(" "));
		while (matcher.find()) {
			addReference(file, withoutTrailingDots(matcher.group(1)), found);
		}
	}

	private void addReference(File file, String imported, List<Reference> found) {
		if (!ignored.test(imported)) {
			found.add(new Reference(imported, resolve(file, imported)));
		}
	}

	/** Drops sentence punctuation, so "see @docs/setup.md." imports {@code docs/setup.md}. */
	private String withoutTrailingDots(String imported) {
		return TRAILING_DOTS.matcher(imported).replaceAll("");
	}

	private File resolve(File file, String imported) {
		if (imported.startsWith("/")) {
			return new File(imported);
		}
		return new File(file.getParentFile(), imported);
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

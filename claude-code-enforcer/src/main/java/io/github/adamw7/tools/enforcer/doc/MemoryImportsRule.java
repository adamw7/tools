package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.doc.ImportGraph.Reference;
import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * Enforcer rule that validates the {@code @path} memory imports of
 * {@code CLAUDE.md}. Claude Code loads every imported file into the session, so an
 * import that does not resolve on disk is silently missing context, and an import
 * cycle or a chain deeper than the loader's five-hop limit means part of the
 * memory is never loaded at all. The rule follows every import recursively and
 * reports a target that does not exist, a circular import, and an import nested
 * deeper than {@code maxDepth} hops.
 * <p>
 * Depth is counted along an import's <em>shortest</em> chain from
 * {@code CLAUDE.md}, which {@link ImportGraph} works out up front. A file two
 * chains reach is loaded as long as one of them is short enough, so judging it by
 * whichever chain the traversal walked into first would report — or miss — a
 * violation depending on the order the imports happen to be written in.
 * <p>
 * Imports are recognised the way Claude Code evaluates them: an {@code @} preceded
 * by start-of-line or whitespace and followed by a path — one carrying a directory
 * separator, an extension, or both — outside fenced code blocks, HTML comments and
 * inline code spans, so neither {@code `@claude`} nor a bare {@code @claude} in
 * prose is an import, and neither is one an author commented out. A
 * home-relative import ({@code @~/...}) points at machine-specific state a build
 * cannot see and is skipped, as is any import listed in {@code ignoredImports}.
 * Each file is scanned once; all problems found are reported together.
 */
@Named("memoryImports")
public class MemoryImportsRule extends ClaudeCodeEnforcerRule {

	private static final int DEFAULT_MAX_DEPTH = 5;

	/** The {@code CLAUDE.md} file whose imports are validated. Injected from the rule configuration. */
	private File claudeMdFile;

	/** Maximum allowed import nesting, in hops from the root file. */
	private int maxDepth = DEFAULT_MAX_DEPTH;

	/** Optional import paths to skip verbatim, e.g. a path only present on developer machines. */
	private List<String> ignoredImports;

	/**
	 * One walk of the import graph: the chain currently being followed, which is
	 * what a cycle is detected against, the files already scanned, and the
	 * problems found so far.
	 */
	private record Traversal(ImportGraph graph, Deque<Path> chain, Set<Path> scanned, List<String> violations) {

		static Traversal of(ImportGraph graph) {
			return new Traversal(graph, new ArrayDeque<>(), new LinkedHashSet<>(), new ArrayList<>());
		}
	}

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(claudeMdFile, "claudeMdFile");
		requireExists(claudeMdFile, "CLAUDE.md");
		File root = claudeMdFile.getAbsoluteFile();
		Traversal traversal = Traversal.of(ImportGraph.from(root, this::isIgnored, this::debug));
		scan(root, traversal);
		report("Memory imports are not well formed:", traversal.violations());
	}

	private void scan(File file, Traversal traversal) {
		Path path = ImportGraph.normalized(file);
		traversal.scanned().add(path);
		traversal.chain().push(path);
		for (Reference reference : traversal.graph().importsOf(file)) {
			checkImport(file, reference, traversal);
		}
		traversal.chain().pop();
	}

	private void checkImport(File file, Reference reference, Traversal traversal) {
		Path path = ImportGraph.normalized(reference.target());
		if (traversal.chain().contains(path)) {
			traversal.violations().add(file + " has a circular import: @" + reference.text());
		} else if (!reference.target().isFile()) {
			traversal.violations().add(file + " imports a missing file: @" + reference.text()
					+ " (resolved to " + reference.target() + ")");
		} else if (traversal.graph().hopsTo(reference.target()) > maxDepth) {
			traversal.violations().add(file + " import @" + reference.text() + " is nested deeper than " + maxDepth
					+ " hops and will not be loaded");
		} else if (!traversal.scanned().contains(path)) {
			scan(reference.target(), traversal);
		}
	}

	private boolean isIgnored(String imported) {
		return ignoredImports != null && ignoredImports.contains(imported);
	}

	/** Reached only for an import target that cannot be read, so the log is looked up lazily. */
	private void debug(String message) {
		getLog().debug(message);
	}

	void setClaudeMdFile(File claudeMdFile) {
		this.claudeMdFile = claudeMdFile;
	}

	void setMaxDepth(int maxDepth) {
		this.maxDepth = maxDepth;
	}

	void setIgnoredImports(List<String> ignoredImports) {
		this.ignoredImports = ignoredImports;
	}
}

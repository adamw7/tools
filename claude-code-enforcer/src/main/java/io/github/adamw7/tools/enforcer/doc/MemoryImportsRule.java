package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.MarkdownDocument;

/**
 * Enforcer rule that validates the {@code @path} memory imports of
 * {@code CLAUDE.md}. Claude Code loads every imported file into the session, so
 * an import that does not resolve on disk is silently missing context, and an
 * import cycle or a chain deeper than the loader's five-hop limit means part of
 * the memory is never loaded at all. The rule follows every import recursively
 * and reports a target that does not exist, a circular import, and an import
 * nested deeper than {@code maxDepth} hops.
 * <p>
 * Imports are recognised the way Claude Code evaluates them: an {@code @}
 * preceded by start-of-line or whitespace and followed by a path, outside fenced
 * code blocks and inline code spans, so {@code `@claude`} in prose is not an
 * import. A home-relative import ({@code @~/...}) is skipped because it points
 * at machine-specific state a build cannot see, and any import listed in
 * {@code ignoredImports} is skipped verbatim. Each file is scanned once; all
 * problems found are reported together.
 */
@Named("memoryImports")
public class MemoryImportsRule extends ClaudeCodeEnforcerRule {

	private static final Pattern IMPORT = Pattern.compile("(?<=^|\\s)@([A-Za-z0-9_./-]+)");
	private static final Pattern CODE_SPAN = Pattern.compile("`[^`]*`");
	private static final int DEFAULT_MAX_DEPTH = 5;

	/** The {@code CLAUDE.md} file whose imports are validated. Injected from the rule configuration. */
	private File claudeMdFile;

	/** Maximum allowed import nesting, in hops from the root file. */
	private int maxDepth = DEFAULT_MAX_DEPTH;

	/** Optional import paths to skip verbatim, e.g. a path only present on developer machines. */
	private List<String> ignoredImports;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(claudeMdFile, "claudeMdFile");
		requireExists(claudeMdFile, "CLAUDE.md");
		List<String> violations = new ArrayList<>();
		scan(claudeMdFile.getAbsoluteFile(), 0, new ArrayDeque<>(), new LinkedHashSet<>(), violations);
		report("Memory imports are not well formed:", violations);
	}

	private void scan(File file, int depth, Deque<Path> stack, Set<Path> visited, List<String> violations) {
		Path path = normalized(file);
		visited.add(path);
		stack.push(path);
		for (String imported : importsIn(file)) {
			checkImport(file, imported, depth, stack, visited, violations);
		}
		stack.pop();
	}

	private void checkImport(File file, String imported, int depth, Deque<Path> stack, Set<Path> visited,
			List<String> violations) {
		if (isIgnored(imported)) {
			return;
		}
		File target = resolve(file, imported);
		Path path = normalized(target);
		if (stack.contains(path)) {
			violations.add(file + " has a circular import: @" + imported);
		} else if (!target.isFile()) {
			violations.add(file + " imports a missing file: @" + imported + " (resolved to " + target + ")");
		} else if (depth + 1 > maxDepth) {
			violations.add(file + " import @" + imported + " is nested deeper than " + maxDepth
					+ " hops and will not be loaded");
		} else if (!visited.contains(path)) {
			scan(target, depth + 1, stack, visited, violations);
		}
	}

	private boolean isIgnored(String imported) {
		return ignoredImports != null && ignoredImports.contains(imported);
	}

	private File resolve(File file, String imported) {
		if (imported.startsWith("/")) {
			return new File(imported);
		}
		return new File(file.getParentFile(), imported);
	}

	private Path normalized(File file) {
		return file.toPath().toAbsolutePath().normalize();
	}

	private List<String> importsIn(File file) {
		MarkdownDocument document = MarkdownDocument.parse(readSafely(file));
		List<String> imports = new ArrayList<>();
		for (int i = 0; i < document.lineCount(); i++) {
			collectLineImports(document, i, imports);
		}
		return imports;
	}

	private void collectLineImports(MarkdownDocument document, int index, List<String> imports) {
		if (document.isInsideFence(index)) {
			return;
		}
		Matcher matcher = IMPORT.matcher(CODE_SPAN.matcher(document.line(index)).replaceAll(" "));
		while (matcher.find()) {
			imports.add(withoutTrailingDots(matcher.group(1)));
		}
	}

	/** Drops sentence punctuation, so "see @docs/setup.md." imports {@code docs/setup.md}. */
	private String withoutTrailingDots(String imported) {
		String path = imported;
		while (path.endsWith(".")) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

	/**
	 * The file's content, or empty when it cannot be read as text. An imported
	 * file may be any format, so a binary target is treated as a leaf rather than
	 * a failure — its existence has already been verified.
	 */
	private String readSafely(File file) {
		try {
			return Files.readString(file.toPath());
		} catch (IOException e) {
			getLog().debug("Skipping unreadable import target " + file + ": " + e.getMessage());
			return "";
		}
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

	@Override
	public String toString() {
		return String.format("MemoryImportsRule[claudeMdFile=%s]", claudeMdFile);
	}
}

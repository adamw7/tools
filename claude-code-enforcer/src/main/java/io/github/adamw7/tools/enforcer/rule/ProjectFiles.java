package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * The file-system helpers the rules share: the null-safe {@link File#listFiles}
 * wrappers, the {@code .md} handling, the "directory must exist" check, and the
 * normalised path a file is keyed by. Every rule that scans a directory or
 * compares two paths needs some of these, so they live here rather than once per
 * feature package — the layering forbids those packages from reaching sideways
 * into each other, so a helper only one of them owned had to be written twice.
 * <p>
 * Listings are returned sorted by natural {@link File} order.
 * {@link File#listFiles} yields entries in an unspecified, filesystem-dependent
 * order, which would let a rule report its violations differently run to run and
 * so churn both the HTML report and a recorded baseline.
 */
public final class ProjectFiles {

	private static final String MARKDOWN_SUFFIX = ".md";

	private ProjectFiles() {
	}

	/** Fails when a configured directory is missing, which is a build-setup mistake. */
	public static void requireDirectory(File directory, String label) throws EnforcerRuleException {
		if (!directory.isDirectory()) {
			throw new EnforcerRuleException(label + " directory does not exist at " + directory);
		}
	}

	/** The regular files directly in {@code directory}, sorted; empty when it cannot be listed. */
	public static List<File> filesIn(File directory) {
		return listed(directory, File::isFile);
	}

	/** The {@code *.md} files directly in {@code directory}, sorted; empty when it cannot be listed. */
	public static List<File> markdownFilesIn(File directory) {
		return listed(directory, file -> file.isFile() && isMarkdown(file.toPath()));
	}

	/** The immediate subdirectories of {@code directory}, sorted; empty when it cannot be listed. */
	public static List<File> subdirectoriesOf(File directory) {
		return listed(directory, File::isDirectory);
	}

	/**
	 * True when {@code path} names a Markdown document. Takes a {@link Path} because
	 * that is what a {@link ScanTargets} predicate is handed, which is how the rules
	 * that budget or scan a tree of documents select theirs.
	 */
	public static boolean isMarkdown(Path path) {
		return path.getFileName().toString().endsWith(MARKDOWN_SUFFIX);
	}

	/** The file name with the {@code .md} suffix stripped. */
	public static String markdownBaseName(File markdown) {
		String name = markdown.getName();
		return name.substring(0, name.length() - MARKDOWN_SUFFIX.length());
	}

	/** The absolute, dot-segment-free path a file is keyed and compared by. */
	public static Path normalized(File file) {
		return normalized(file.toPath());
	}

	/** The absolute, dot-segment-free path a file is keyed and compared by. */
	public static Path normalized(Path path) {
		return path.toAbsolutePath().normalize();
	}

	private static List<File> listed(File directory, FileFilter filter) {
		File[] entries = directory.listFiles(filter);
		if (entries == null) {
			return List.of();
		}
		Arrays.sort(entries);
		return List.of(entries);
	}
}

package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.Arrays;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Shared file-system helpers for the definition rules, which all scan a
 * {@code .claude} directory of Markdown definitions. Centralises the
 * {@code .md} handling, the null-safe {@link File#listFiles} wrappers and the
 * "directory must exist" check, so each rule keeps only its own validation
 * logic.
 * <p>
 * Both listing helpers return their entries sorted by natural {@link File}
 * order. {@link File#listFiles} yields entries in an unspecified,
 * filesystem-dependent order, which would let the rules built on these helpers
 * report violations in a different order run-to-run; sorting here makes every
 * rule's output — and the HTML report — stable and diffable.
 */
final class DefinitionFiles {

	private static final String MARKDOWN_SUFFIX = ".md";

	private DefinitionFiles() {
	}

	/** Fails when a configured directory is missing, which is a build-setup mistake. */
	static void verifyDirectory(File directory, String label) throws EnforcerRuleException {
		if (!directory.isDirectory()) {
			throw new EnforcerRuleException(label + " directory does not exist at " + directory);
		}
	}

	/** The {@code *.md} files directly in {@code directory}, sorted, never null. */
	static File[] markdownFiles(File directory) {
		return sorted(directory.listFiles(DefinitionFiles::isMarkdownFile));
	}

	/** The immediate subdirectories of {@code directory}, sorted, never null. */
	static File[] subdirectories(File directory) {
		return sorted(directory.listFiles(File::isDirectory));
	}

	private static File[] sorted(File[] files) {
		if (files == null) {
			return new File[0];
		}
		Arrays.sort(files);
		return files;
	}

	/** The file name with the {@code .md} suffix stripped. */
	static String baseName(File markdown) {
		String name = markdown.getName();
		return name.substring(0, name.length() - MARKDOWN_SUFFIX.length());
	}

	private static boolean isMarkdownFile(File file) {
		return file.isFile() && file.getName().endsWith(MARKDOWN_SUFFIX);
	}
}

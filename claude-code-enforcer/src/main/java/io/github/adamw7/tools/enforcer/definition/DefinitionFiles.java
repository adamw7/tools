package io.github.adamw7.tools.enforcer.definition;

import java.io.File;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Shared file-system helpers for the definition rules, which all scan a
 * {@code .claude} directory of Markdown definitions. Centralises the
 * {@code .md} handling, the null-safe {@link File#listFiles} wrappers and the
 * "directory must exist" check, so each rule keeps only its own validation
 * logic.
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

	/** The {@code *.md} files directly in {@code directory}, never null. */
	static File[] markdownFiles(File directory) {
		File[] files = directory.listFiles(DefinitionFiles::isMarkdownFile);
		return files != null ? files : new File[0];
	}

	/** The immediate subdirectories of {@code directory}, never null. */
	static File[] subdirectories(File directory) {
		File[] directories = directory.listFiles(File::isDirectory);
		return directories != null ? directories : new File[0];
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

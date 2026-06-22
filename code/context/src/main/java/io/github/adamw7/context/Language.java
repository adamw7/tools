package io.github.adamw7.context;

import java.util.Locale;

/**
 * A programming language supported by the context finder. Each language is
 * identified by the file extension of its source files, which is how the
 * regex-based {@link Finder} resolves a referenced class name back to a
 * concrete source file and how {@link io.github.adamw7.context.tree.ProjectTreeBuilder}
 * recognises the project's source files.
 */
public enum Language {

	JAVA(".java"),
	KOTLIN(".kt"),
	SCALA(".scala");

	private final String extension;

	Language(String extension) {
		this.extension = extension;
	}

	/**
	 * Resolves a language from its case-insensitive name (e.g. {@code "java"} or
	 * {@code "kotlin"}), so callers such as the MCP tools can accept the language
	 * as a plain string argument.
	 *
	 * @throws IllegalArgumentException if no language matches the given name
	 */
	public static Language fromName(String name) {
		return valueOf(name.trim().toUpperCase(Locale.ROOT));
	}

	public String extension() {
		return extension;
	}
}

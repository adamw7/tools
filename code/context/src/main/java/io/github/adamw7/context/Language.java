package io.github.adamw7.context;

/**
 * A programming language supported by the context finder. Each language is
 * identified by the file extension of its source files, which is how the
 * regex-based {@link Finder} resolves a referenced class name back to a
 * concrete source file and how {@link io.github.adamw7.context.tree.ProjectTreeBuilder}
 * recognises the project's source files.
 */
public enum Language {

	JAVA(".java"),
	KOTLIN(".kt");

	private final String extension;

	Language(String extension) {
		this.extension = extension;
	}

	public String extension() {
		return extension;
	}
}

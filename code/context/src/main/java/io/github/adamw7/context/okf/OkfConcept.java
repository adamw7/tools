package io.github.adamw7.context.okf;

import java.time.Instant;
import java.util.List;

/**
 * One concept of an {@link OkfBundle} — in this producer's vocabulary, a single
 * file of the scanned project — described in the terms the Open Knowledge Format
 * asks for, before it is rendered to markdown. Separating the facts about a
 * concept from the document they end up in lets the same description serve both
 * the concept's own frontmatter and the {@code index.md} entry that links to it,
 * which is what the specification recommends.
 *
 * @param path        bundle-relative path of the concept document
 * @param type        the one mandatory frontmatter field, e.g. {@code Java Source File}
 * @param title       human-readable name, here the file name
 * @param description single sentence summarising the concept
 * @param resource    project-relative path of the file the concept describes
 * @param tags        cross-cutting categories
 * @param dependencies rendered markdown for each dependency, linked when the
 *                     dependency resolves to another concept of the same bundle
 */
public record OkfConcept(String path, String type, String title, String description, String resource,
		List<String> tags, List<String> dependencies) {

	private static final String DEPENDENCIES_HEADING = "# Dependencies";
	private static final String NO_DEPENDENCIES = "This file depends on no other file in the project.";
	private static final String BULLET = "* ";

	public OkfConcept {
		tags = List.copyOf(tags);
		dependencies = List.copyOf(dependencies);
	}

	/**
	 * Renders the concept as a markdown document: the YAML frontmatter, then a
	 * dependencies section listing the other concepts this file relies on.
	 */
	public OkfDocument render(String producer, Instant generatedAt) {
		StringBuilder builder = new StringBuilder(frontmatter(producer, generatedAt));
		builder.append(System.lineSeparator()).append(DEPENDENCIES_HEADING).append(System.lineSeparator())
				.append(System.lineSeparator());
		appendDependencies(builder);
		return new OkfDocument(path, builder.toString());
	}

	private String frontmatter(String producer, Instant generatedAt) {
		return new OkfFrontmatter(type)
				.title(title)
				.description(description)
				.resource(resource)
				.tags(tags)
				.generated(producer, generatedAt)
				.render();
	}

	private void appendDependencies(StringBuilder builder) {
		if (dependencies.isEmpty()) {
			builder.append(NO_DEPENDENCIES).append(System.lineSeparator());
			return;
		}
		dependencies.forEach(dependency ->
				builder.append(BULLET).append(dependency).append(System.lineSeparator()));
	}
}

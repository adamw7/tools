package io.github.adamw7.context.okf;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The YAML frontmatter block that opens every OKF concept document. The Open
 * Knowledge Format makes exactly one field mandatory — {@code type} — and
 * recommends {@code title}, {@code description}, {@code resource} and
 * {@code tags}; v0.2 records how a concept was produced as
 * {@code generated: { by, at }}. This builder renders that subset in a fixed
 * order and skips the fields a caller left unset, so callers describe a concept
 * in domain terms rather than assembling YAML by hand.
 * <p>
 * Every scalar is emitted double-quoted, which keeps a colon in a description or
 * a {@code #} in a path from changing the meaning of the block, and the actor of
 * {@code generated.by} follows the specification's {@code <producer>/<version>}
 * convention.
 */
public class OkfFrontmatter {

	private static final String DELIMITER = "---";
	private static final String SEPARATOR = ": ";

	private final String type;
	private String title;
	private String description;
	private String resource;
	private List<String> tags = List.of();
	private String generatedBy;
	private Instant generatedAt;

	public OkfFrontmatter(String type) {
		if (type == null || type.isBlank()) {
			throw new IllegalArgumentException("An OKF concept must declare a non-empty type");
		}
		this.type = type;
	}

	public OkfFrontmatter title(String newTitle) {
		this.title = newTitle;
		return this;
	}

	public OkfFrontmatter description(String newDescription) {
		this.description = newDescription;
		return this;
	}

	public OkfFrontmatter resource(String newResource) {
		this.resource = newResource;
		return this;
	}

	public OkfFrontmatter tags(List<String> newTags) {
		this.tags = List.copyOf(newTags);
		return this;
	}

	/**
	 * Records the actor that produced the concept and when its content last
	 * changed. The instant is truncated to whole seconds, the granularity the
	 * specification's ISO 8601 examples use.
	 */
	public OkfFrontmatter generated(String actor, Instant when) {
		this.generatedBy = actor;
		this.generatedAt = when;
		return this;
	}

	public String render() {
		StringBuilder builder = new StringBuilder();
		builder.append(DELIMITER).append(System.lineSeparator());
		appendScalar(builder, "type", type);
		appendScalar(builder, "title", title);
		appendScalar(builder, "description", description);
		appendScalar(builder, "resource", resource);
		appendTags(builder);
		appendGenerated(builder);
		builder.append(DELIMITER).append(System.lineSeparator());
		return builder.toString();
	}

	private void appendScalar(StringBuilder builder, String key, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		builder.append(key).append(SEPARATOR).append(quote(value)).append(System.lineSeparator());
	}

	private void appendTags(StringBuilder builder) {
		if (tags.isEmpty()) {
			return;
		}
		String rendered = tags.stream().map(OkfFrontmatter::quote).collect(Collectors.joining(", ", "[", "]"));
		builder.append("tags").append(SEPARATOR).append(rendered).append(System.lineSeparator());
	}

	private void appendGenerated(StringBuilder builder) {
		if (generatedBy == null || generatedAt == null) {
			return;
		}
		builder.append("generated").append(SEPARATOR)
				.append("{ by: ").append(quote(generatedBy))
				.append(", at: ").append(quote(generatedAt.truncatedTo(ChronoUnit.SECONDS).toString()))
				.append(" }").append(System.lineSeparator());
	}

	private static String quote(String value) {
		return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
	}
}

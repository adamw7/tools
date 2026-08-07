package io.github.adamw7.context.okf;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * The YAML frontmatter block that opens every OKF concept document. The Open
 * Knowledge Format makes exactly one field mandatory — {@code type} — and
 * recommends {@code title}, {@code description}, {@code resource} and
 * {@code tags}; v0.2 records how a concept was produced as
 * {@code generated: { by, at }}, its actor following the specification's
 * {@code <producer>/<version>} convention. This builder collects that subset,
 * drops the fields a caller left unset, and hands the rest to SnakeYAML in the
 * specification's order.
 * <p>
 * Quoting is the emitter's decision, not this class's. Writing every scalar
 * double-quoted and escaping the two characters that seemed to matter suited the
 * values a concept usually carries and nothing else: a title holding a newline or
 * a control character — both of which a file name on disk may — went straight into
 * the middle of a quoted scalar, and the block no longer parsed as YAML at all.
 */
public class OkfFrontmatter {

	private static final String DELIMITER = "---";

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
		return DELIMITER + System.lineSeparator() + emitter().dump(fields())
				+ DELIMITER + System.lineSeparator();
	}

	/** Built here, not as the setters run, so the block keeps the specification's order. */
	private Map<String, Object> fields() {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("type", type);
		put(fields, "title", title);
		put(fields, "description", description);
		put(fields, "resource", resource);
		if (!tags.isEmpty()) {
			fields.put("tags", tags);
		}
		if (generatedBy != null && generatedAt != null) {
			fields.put("generated", generated());
		}
		return fields;
	}

	private Map<String, String> generated() {
		Map<String, String> generated = new LinkedHashMap<>();
		generated.put("by", generatedBy);
		generated.put("at", generatedAt.truncatedTo(ChronoUnit.SECONDS).toString());
		return generated;
	}

	private static void put(Map<String, Object> fields, String key, String value) {
		if (value != null && !value.isBlank()) {
			fields.put(key, value);
		}
	}

	/** Block style, and no width: SnakeYAML otherwise wraps a long description at 80 columns. */
	private static Yaml emitter() {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setLineBreak(DumperOptions.LineBreak.getPlatformLineBreak());
		options.setWidth(Integer.MAX_VALUE);
		return new Yaml(options);
	}
}

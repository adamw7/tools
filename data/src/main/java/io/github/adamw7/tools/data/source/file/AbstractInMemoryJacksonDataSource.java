package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base for the in-memory sources that read a whole Jackson document and flatten it
 * into {@link #fieldsMap} as {@code key -> value} pairs keyed by dotted path, using
 * {@code .} for nested object fields and {@code [index]} for array elements (for
 * example {@code people[0].address.city}).
 *
 * <p>JSON and YAML differ only in the {@link ObjectMapper} that reads them, so the
 * flattening lives here once instead of being written out per syntax. It walks the
 * same tree the streaming {@link AbstractIterableJacksonDataSource} pulls token by
 * token, so the same document read as JSON or YAML, in memory or iteratively,
 * yields the same rows.</p>
 */
public abstract class AbstractInMemoryJacksonDataSource extends AbstractInMemoryMapDataSource {

	protected AbstractInMemoryJacksonDataSource(InputStream inputStream) {
		super(inputStream);
	}

	protected AbstractInMemoryJacksonDataSource(String filePath) {
		super(filePath);
	}

	protected AbstractInMemoryJacksonDataSource(String filePath, AllowedPaths allowedPaths) {
		super(filePath, allowedPaths);
	}

	/**
	 * The mapper for this source's syntax. It is asked for while the base constructor
	 * parses, so it must answer from a constant rather than from instance state the
	 * subclass has not assigned yet.
	 */
	protected abstract ObjectMapper mapper();

	@Override
	protected void parse() {
		StringBuilder content = new StringBuilder();
		while (hasNextLine()) {
			content.append(scanner.nextLine()).append('\n');
		}
		flatten("", read(content.toString()));
	}

	private JsonNode read(String content) {
		try {
			return mapper().readTree(content);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid " + mapper().getFactory().getFormatName() + " input", e);
		}
	}

	private void flatten(String key, JsonNode value) {
		if (value.isObject()) {
			value.properties().forEach(field -> flatten(qualify(key, field.getKey()), field.getValue()));
		} else if (value.isArray()) {
			for (int index = 0; index < value.size(); index++) {
				flatten(key + "[" + index + "]", value.get(index));
			}
		} else {
			fieldsMap.put(key, value.asText());
		}
	}

	private String qualify(String prefix, String field) {
		return prefix.isEmpty() ? field : prefix + "." + field;
	}
}

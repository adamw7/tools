package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class InMemoryYAMLDataSource extends AbstractInMemoryMapDataSource {

	public InMemoryYAMLDataSource(InputStream inputStream) {
		super(inputStream);
	}

	public InMemoryYAMLDataSource(String filePath) {
		super(filePath);
	}

	@Override
	protected void parse() {
		StringBuilder yamlContent = new StringBuilder();
		while (scanner.hasNextLine()) {
			yamlContent.append(scanner.nextLine()).append("\n");
		}
		parseYAML(yamlContent.toString());
	}

	private void parseYAML(String yamlString) {
		try {
			ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
			Object parsed = yamlMapper.readValue(yamlString, Object.class);
			if (parsed instanceof Map<?, ?> map) {
				flattenMap("", map);
			} else if (parsed instanceof List<?> list) {
				flattenList("", list);
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse YAML", e);
		}
	}

	private void flattenMap(String prefix, Map<?, ?> map) {
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String key = prefix.isEmpty() ? String.valueOf(entry.getKey())
					: prefix + "." + entry.getKey();
			flattenValue(key, entry.getValue());
		}
	}

	private void flattenList(String prefix, List<?> list) {
		for (int i = 0; i < list.size(); i++) {
			flattenValue(prefix + "[" + i + "]", list.get(i));
		}
	}

	private void flattenValue(String key, Object value) {
		if (value instanceof Map<?, ?> map) {
			flattenMap(key, map);
		} else if (value instanceof List<?> list) {
			flattenList(key, list);
		} else {
			fieldsMap.put(key, String.valueOf(value));
		}
	}
}

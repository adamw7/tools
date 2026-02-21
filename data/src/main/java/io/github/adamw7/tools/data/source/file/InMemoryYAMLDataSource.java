package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class InMemoryYAMLDataSource extends AbstractFileSource implements InMemoryDataSource, IterableDataSource {
	private final Map<String, String> fieldsMap = new HashMap<>();
	private Iterator<String> mapIterator;

	public InMemoryYAMLDataSource(InputStream inputStream) {
		super(inputStream);
		scanner = createScanner(inputStream);
		parse();
	}

	public InMemoryYAMLDataSource(String filePath) {
		super(filePath);
		parse();
	}

	private void parse() {
		StringBuilder yamlContent = new StringBuilder();
		while (scanner.hasNextLine()) {
			yamlContent.append(scanner.nextLine()).append("\n");
		}
		parseYAML(yamlContent.toString());
	}

	@SuppressWarnings("unchecked")
	private void parseYAML(String yamlString) {
		try {
			ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
			Object parsed = yamlMapper.readValue(yamlString, Object.class);
			if (parsed instanceof Map) {
				flattenMap("", (Map<String, Object>) parsed);
			} else if (parsed instanceof List) {
				flattenList("", (List<Object>) parsed);
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse YAML", e);
		}
	}

	@SuppressWarnings("unchecked")
	private void flattenMap(String prefix, Map<String, Object> map) {
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
			Object value = entry.getValue();
			if (value instanceof Map) {
				flattenMap(key, (Map<String, Object>) value);
			} else if (value instanceof List) {
				flattenList(key, (List<Object>) value);
			} else {
				fieldsMap.put(key, String.valueOf(value));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void flattenList(String prefix, List<Object> list) {
		for (int i = 0; i < list.size(); i++) {
			String key = prefix + "[" + i + "]";
			Object value = list.get(i);
			if (value instanceof Map) {
				flattenMap(key, (Map<String, Object>) value);
			} else if (value instanceof List) {
				flattenList(key, (List<Object>) value);
			} else {
				fieldsMap.put(key, String.valueOf(value));
			}
		}
	}

	@Override
	public void open() {
		if (opened) {
			throw new IllegalStateException("DataSource is already open");
		}
		mapIterator = fieldsMap.keySet().iterator();
		opened = true;
	}

	@Override
	public String[] nextRow() {
		checkIfOpen();
		if (mapIterator.hasNext()) {
			String key = mapIterator.next();
			String value = fieldsMap.get(key);
			return new String[] { key, value };
		}
		return null;
	}

	@Override
	public boolean hasMoreData() {
		checkIfOpen();
		return mapIterator.hasNext();
	}

	@Override
	public void reset() {
		checkIfOpen();
		mapIterator = fieldsMap.keySet().iterator();
	}

	public Iterator<String[]> iterator() {
		return new Iterator<>() {
			@Override
			public boolean hasNext() {
				return hasMoreData();
			}

			@Override
			public String[] next() {
				return nextRow();
			}
		};
	}

	@Override
	public String[] getColumnNames() {
		return fieldsMap.keySet().toArray(new String[] {});
	}

	@Override
	public List<String[]> readAll() {
		return super.readAll();
	}
}

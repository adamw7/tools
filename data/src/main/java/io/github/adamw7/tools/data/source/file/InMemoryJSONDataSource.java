package io.github.adamw7.tools.data.source.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;
import io.github.adamw7.tools.data.source.interfaces.IterableDataSource;

public class InMemoryJSONDataSource extends AbstractFileSource implements InMemoryDataSource, IterableDataSource {
	private final Map<String, String> fieldsMap = new HashMap<>();
	private Iterator<String> mapIterator;

	public InMemoryJSONDataSource(String filePath) {
		super(filePath);
		StringBuilder jsonContent = new StringBuilder();
		while (scanner.hasNextLine()) {
			jsonContent.append(scanner.nextLine());
		}
		parseJSON(jsonContent.toString());
	}

	private void parseJSON(String jsonString) throws JSONException {
		JSONObject jsonArray = new JSONObject(jsonString);
		extractFieldNames(jsonArray);
		flattenJSON(jsonArray);
	}

	private void extractFieldNames(JSONObject jsonObject) throws JSONException {
		for (String key : jsonObject.keySet()) {
			Object value = jsonObject.get(key);
			fieldsMap.put(key, String.valueOf(value));
			if (value instanceof JSONObject jsonObjectValue) {
				extractFieldNames(jsonObjectValue);
			}
		}
	}

	private void flattenJSON(JSONArray jsonArray) throws JSONException {
		for (int i = 0; i < jsonArray.length(); i++) {
			Object value = jsonArray.get(i);
			if (value instanceof JSONArray jsonArrayValue) {
				flattenJSON(jsonArrayValue);
			} else if (value instanceof JSONObject jsonObjectValue) {
				flattenJSON(jsonObjectValue);
			}
		}
	}

	private void flattenJSON(JSONObject jsonObject) throws JSONException {
		for (String key : jsonObject.keySet()) {
			Object value = jsonObject.get(key);
			if (value instanceof JSONArray jsonArrayValue) {
				flattenJSON(jsonArrayValue);
			} else if (value instanceof JSONObject jsonObjectValue) {
				flattenJSON(jsonObjectValue);
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
		open();
		List<String[]> data = new ArrayList<>();
		while (hasMoreData()) {
			String[] row = nextRow();
			if (row != null) {
				data.add(row);
			}
		}
		return data;
	}
}

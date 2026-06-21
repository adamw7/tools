package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class InMemoryJSONDataSource extends AbstractInMemoryMapDataSource {

	public InMemoryJSONDataSource(InputStream inputStream) {
		super(inputStream);
	}

	public InMemoryJSONDataSource(String filePath) {
		super(filePath);
	}

	@Override
	protected void parse() {
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
}

package io.github.adamw7.tools.data.source.file;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileReader;
import java.io.IOException;
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

public class InMemoryJSONDataSource implements InMemoryDataSource, IterableDataSource, AutoCloseable, Closeable {
    private BufferedReader reader;
    private final Map<String, String> fieldsMap = new HashMap<>();
    private boolean opened = false;
    private Iterator<String> mapIterator;

    public InMemoryJSONDataSource(String filePath) {
        try {
            reader = new BufferedReader(new FileReader(filePath));
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            parseJSON(jsonContent.toString());
        } catch (IOException | JSONException e) {
            throw new RuntimeException("Error opening or parsing JSON file: " + e.getMessage());
        }
    }

    private void parseJSON(String jsonString) throws JSONException {
        JSONArray jsonArray = new JSONArray(jsonString);
        extractFieldNames(jsonArray);
        flattenJSON(jsonArray);
    }

    private void extractFieldNames(JSONArray jsonArray) throws JSONException {
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                extractFieldNames((JSONObject) value);
            }
        }
    }

    private void extractFieldNames(JSONObject jsonObject) throws JSONException {
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);
            fieldsMap.put(key, String.valueOf(value));
            if (value instanceof JSONObject) {
                extractFieldNames((JSONObject) value);
            }
        }
    }

    private void flattenJSON(JSONArray jsonArray) throws JSONException {
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONArray) {
                flattenJSON((JSONArray) value);
            } else if (value instanceof JSONObject) {
                flattenJSON((JSONObject) value);
            }
        }
    }

    private void flattenJSON(JSONObject jsonObject) throws JSONException {
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);
            if (value instanceof JSONArray) {
                flattenJSON((JSONArray) value);
            } else if (value instanceof JSONObject) {
                flattenJSON((JSONObject) value);
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
        if (!opened) {
            throw new IllegalStateException("DataSource is not open");
        }
        if (mapIterator.hasNext()) {
        	String key = mapIterator.next();
        	String value = fieldsMap.get(key);
        	return new String[] {key, value};
        }
        return null;
    }

    @Override
    public boolean hasMoreData() {
        if (!opened) {
            throw new IllegalStateException("DataSource is not open");
        }
        return mapIterator.hasNext();
    }

    @Override
    public void reset() {
        if (!opened) {
            throw new IllegalStateException("DataSource is not open");
        }
        mapIterator = fieldsMap.keySet().iterator();
    }

    @Override
    public void close() throws IOException {
        if (opened) {
            reader.close();
            opened = false;
        }
    }

    public Iterator<String[]> iterator() {
        return new Iterator<String[]>() {
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

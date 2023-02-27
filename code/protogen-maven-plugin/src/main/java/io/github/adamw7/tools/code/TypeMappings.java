package io.github.adamw7.tools.code;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessageV3;

public class TypeMappings {

	private final Map<String, String> mappings = new HashMap<>();
	
	void putJavaTypes() {
		mappings.put("STRING", "String");
		mappings.put("INT", "int");
	}

	public TypeMappings(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		putJavaTypes();
		putCustomTypes(allMessages);
	}

	private void putCustomTypes(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		for (Class<? extends GeneratedMessageV3> clazz : allMessages) {
			mappings.put(clazz.getSimpleName(), clazz.getSimpleName());
			System.err.println("putting by " + clazz.getSimpleName());
		}
	}

	public String get(FieldDescriptor field) {
		String key = field.getType().getJavaType().name();
		System.err.println("getting by " + key);
		
		return mappings.get(key);
	}
}

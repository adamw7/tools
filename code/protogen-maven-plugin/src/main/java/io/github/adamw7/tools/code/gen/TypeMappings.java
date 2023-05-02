package io.github.adamw7.tools.code.gen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessageV3;

public class TypeMappings {

	private final Map<String, String> mappings = new HashMap<>();
	
	void putJavaTypes() {
		mappings.put("STRING", "String");
		mappings.put("INT", "int");
		mappings.put("LONG", "long");
		mappings.put("FLOAT", "float");
		mappings.put("BOOLEAN", "boolean");		
		mappings.put("DOUBLE", "double");
		mappings.put("BYTE_STRING", "com.google.protobuf.ByteString");
	}

	public TypeMappings(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		putJavaTypes();
		putCustomTypes(allMessages);
	}

	private void putCustomTypes(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		for (Class<? extends GeneratedMessageV3> clazz : allMessages) {
			mappings.put(clazz.getSimpleName(), clazz.getSimpleName());
		}
	}

	public String get(FieldDescriptor field) {
		String key = field.getType().getJavaType().name();
		
		if (key.equals("ENUM")) {
			return handleEnum(field);
		} if (key.equals("MESSAGE") && field.isMapField()) {
			return handleMap(field);
		} else {
			String type = mappings.get(key);
			if (type == null) {
				throw new IllegalArgumentException("Could not find type mapping for key: " + key);
			} else {
				return type;
			}
		}		
	}

	private String handleMap(FieldDescriptor field) {
		List<Descriptor> nestedTypes = field.getContainingType().getNestedTypes();
		List<FieldDescriptor> fields = nestedTypes.get(0).getFields();
		String key = wrapIfNeeded(get(fields.get(0)));
		String value = wrapIfNeeded(get(fields.get(1)));
		return "java.util.Map<" + key + "," + value + ">";
	}

	private String wrapIfNeeded(String type) {
		if (type.equals("int")) {
			return "Integer";
		} else {
			return type;
		}
	}

	private String handleEnum(FieldDescriptor field) {
		return Utils.getSuffixOf(field.getEnumType().getFullName(), 2, ".");
	}
}

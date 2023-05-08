package io.github.adamw7.tools.code.gen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessageV3;

public class TypeMappings {

	private final Map<String, String> mappings = new HashMap<>();
	private final Map<String, String> primitiveToObjectMap = new HashMap<>();
	
	void putJavaTypes() {
		mappings.put("STRING", String.class.getSimpleName());
		mappings.put("INT", int.class.getName());
		mappings.put("LONG", long.class.getName());
		mappings.put("FLOAT", float.class.getName());
		mappings.put("BOOLEAN", boolean.class.getName());		
		mappings.put("DOUBLE", double.class.getName());
		mappings.put("BYTE_STRING", ByteString.class.getName());
	}

	public TypeMappings(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		putJavaTypes();		
		putPrimitiveTypes();
		putCustomTypes(allMessages);		
	}

	private void putPrimitiveTypes() {
		primitiveToObjectMap.put("int", Integer.class.getSimpleName());
		primitiveToObjectMap.put("long", Long.class.getSimpleName());
		primitiveToObjectMap.put("boolean", Boolean.class.getSimpleName());
		primitiveToObjectMap.put("double", Double.class.getSimpleName());
		primitiveToObjectMap.put("float", Float.class.getSimpleName());
		primitiveToObjectMap.put("char", Character.class.getSimpleName());
		primitiveToObjectMap.put("short", Short.class.getSimpleName());		
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
		} else if (key.equals("MESSAGE") && field.isMapField()) {
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
		List<Descriptor> descriptors = field.getContainingType().getNestedTypes();
		for (Descriptor descriptor : descriptors) {
			if (belongsTo(field, descriptor)) {
				FieldDescriptor keyDesc = descriptor.findFieldByName("key");
				FieldDescriptor valueDesc = descriptor.findFieldByName("value");
				
				String key = wrapIfNeeded(get(keyDesc));		
				String value = wrapIfNeeded(get(valueDesc));
				return Map.class.getName() + "<" + key + "," + value + ">";		
			}
		}
		throw new IllegalStateException("Have not found types for map: " + field.getFullName());
	}

	private boolean belongsTo(FieldDescriptor field, Descriptor descriptor) {
		return descriptor.getFullName().toLowerCase().contains(field.getJsonName().toLowerCase());
	}

	private String wrapIfNeeded(String type) {
		String value = primitiveToObjectMap.get(type);
		if (value == null) {
			return type;
		} else {
			return value;
		}
	}

	private String handleEnum(FieldDescriptor field) {
		return Utils.getSuffixOf(field.getEnumType().getFullName(), 2, ".");
	}
}

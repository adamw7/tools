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
		mappings.put("STRING", name(String.class));
		mappings.put("INT", name(int.class));
		mappings.put("LONG", name(long.class));
		mappings.put("FLOAT", name(float.class));
		mappings.put("BOOLEAN", name(boolean.class));		
		mappings.put("DOUBLE", name(double.class));
		mappings.put("BYTE_STRING", name(ByteString.class));
	}

	private static String name(Class<?> clazz) {
		if (clazz.isPrimitive() || clazz.getPackage().getName().startsWith("java.lang")) {
			return clazz.getSimpleName();
		} else {
			return clazz.getName();
		}
	}

	public TypeMappings(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		putJavaTypes();		
		putPrimitiveTypes();
		putCustomTypes(allMessages);		
	}

	private void putPrimitiveTypes() {
		primitiveToObjectMap.put(name(int.class), name(Integer.class));
		primitiveToObjectMap.put(name(long.class), name(Long.class));
		primitiveToObjectMap.put(name(boolean.class), name(Boolean.class));
		primitiveToObjectMap.put(name(double.class), name(Double.class));
		primitiveToObjectMap.put(name(float.class), name(Float.class));
		primitiveToObjectMap.put(name(char.class), name(Character.class));
		primitiveToObjectMap.put(name(short.class), name(Short.class));		
	}

	private void putCustomTypes(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		for (Class<? extends GeneratedMessageV3> clazz : allMessages) {
			mappings.put(clazz.getSimpleName(), clazz.getSimpleName());
		}
	}

	public String get(FieldDescriptor field) {
		String key = field.getType().getJavaType().name();
		
		if (field.isRepeated() && !field.isMapField()) {
			return handleRepeated(field);
		} else if (key.equals("ENUM")) {
			return handleEnum(field);
		} else if (key.equals("MESSAGE") && field.isMapField()) {
			return handleMap(field);
		} else if (key.equals("MESSAGE")) { 
			return handleMessage(field);
		} else {
			String type = mappings.get(key);
			if (type == null) {
				throw new IllegalArgumentException("Could not find type mapping for key: " + key);
			} else {
				return type;
			}
		}		
	}

	private String handleRepeated(FieldDescriptor field) {
		String innerType = field.getType().getJavaType().name();
		if (innerType.equals("ENUM")) {
			innerType = handleEnum(field);
		} else {
			innerType = wrapIfNeeded(mappings.get(innerType));			
		}

		return "java.util.List<" + innerType + ">";
	}

	private String handleMessage(FieldDescriptor field) {
		return Utils.getSuffixOf(field.toProto().getTypeName(), 1, ".");
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

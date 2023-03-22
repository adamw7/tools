package io.github.adamw7.tools.code.gen;

import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Utils {

	public static String to(FieldDescriptor fieldDescriptor, String suffix) {
		return Utils.firstToUpper(fieldDescriptor.getName()) + suffix;
	}

	public static String firstToLower(String string) {
		return (String.valueOf(string.charAt(0)).toLowerCase() + string.substring(1));
	}

	public static String firstToUpper(String string) {
		return (String.valueOf(string.charAt(0)).toUpperCase() + string.substring(1));
	}

	public static String getNext(List<FieldDescriptor> fields, FieldDescriptor requiredField, String suffix) {
		for (int i = 0; i < fields.size(); ++i) {
			if (fields.get(i).equals(requiredField)) {
				return i == fields.size() - 1 ? "Optional" + suffix : to(fields.get(i + 1), suffix);
			}
		}
		return "Optional" + suffix;
	}

	public static StringBuilder generateSetter(FieldDescriptor field, TypeMappings mappings) {
		StringBuilder builder = new StringBuilder("set");
		builder.append(firstToUpper(field.getName()));
		builder.append("(").append(mappings.get(field));
		builder.append(" ").append(field.getName()).append(")");
		return builder;
	}

}

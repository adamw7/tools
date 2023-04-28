package io.github.adamw7.tools.code.gen;

import java.util.List;
import java.util.regex.Pattern;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Utils {

	public static String to(FieldDescriptor fieldDescriptor, String suffix) {
		return Utils.firstToUpper(fieldDescriptor.getName()) + suffix;
	}

	public static String firstToLower(String string) {
		return (String.valueOf(string.charAt(0)).toLowerCase() + string.substring(1));
	}

	public static String firstToUpper(String string) {
		return (firstUpper(string) + string.substring(1));
	}

	private static String firstUpper(String string) {
		return String.valueOf(string.charAt(0)).toUpperCase();
	}

	public static String toUpperCamelCase(String s) {
		String[] parts = s.split("_");
		StringBuilder camelCase = new StringBuilder();
		for (String part : parts) {
			camelCase.append(toProperCase(part));
		}
		return camelCase.toString();
	}

	static String toProperCase(String s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}

	public static String getNext(String className, List<FieldDescriptor> fields, FieldDescriptor requiredField, String suffix) {
		for (int i = 0; i < fields.size(); ++i) {
			if (fields.get(i).equals(requiredField)) {
				return i == fields.size() - 1 ? className + "Optional" + suffix : to(fields.get(i + 1), suffix);
			}
		}
		return className + "Optional" + suffix;
	}
	
	public static String getSuffixOf(String type, int howMany, String delimiter) {
		String[] tokens = type.split(Pattern.quote(delimiter));
		String suffix = "";
		for (int i = howMany; i > 0; i--) {
			suffix += tokens[tokens.length - i];
			suffix += delimiter;
		}
		return suffix.substring(0, suffix.length() - 1);
	}

}

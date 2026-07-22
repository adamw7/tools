package io.github.adamw7.tools.code.gen;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Utils {

	public static final String IFC_SUFFIX = "Ifc";
	public static final String IMPL_SUFFIX = "Impl";

	private Utils() {}

	public static String to(FieldDescriptor fieldDescriptor, String suffix) {
		return Utils.firstToUpper(fieldDescriptor.getName()) + suffix;
	}

	public static String firstToLower(String string) {
		if (string.isEmpty()) {
			return string;
		}
		return (String.valueOf(string.charAt(0)).toLowerCase() + string.substring(1));
	}

	public static String firstToUpper(String string) {
		if (string.isEmpty()) {
			return string;
		}
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
		if (s.isEmpty()) {
			// A leading, trailing or doubled underscore in a proto name yields an
			// empty split part; it contributes nothing to the camel-case name.
			return s;
		}
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}
	
	public static String getNextIfc(String className, List<FieldDescriptor> fields, FieldDescriptor requiredField) {
		return getNext(className, fields, requiredField, IFC_SUFFIX);
	}

	public static String getNextImpl(String className, List<FieldDescriptor> fields, FieldDescriptor requiredField) {
		return getNext(className, fields, requiredField, IMPL_SUFFIX);
	}

	private static String getNext(String className, List<FieldDescriptor> fields, FieldDescriptor requiredField, String suffix) {
		for (int i = 0; i < fields.size(); ++i) {
			if (fields.get(i).equals(requiredField)) {
				return className + (i == fields.size() - 1 ? "Optional" + suffix : to(fields.get(i + 1), suffix));
			}
		}
		return className + "Optional" + suffix;
	}
	
	public static String getSuffixOf(String type, int howMany, String delimiter) {
		String[] tokens = type.split(Pattern.quote(delimiter));
		StringBuilder suffixBuilder = new StringBuilder();
		for (int i = howMany; i > 0; i--) {
			suffixBuilder.append(tokens[tokens.length - i]);
			suffixBuilder.append(delimiter);
		}
		String suffix = suffixBuilder.toString();
		return suffix.substring(0, suffix.length() - 1);
	}

	public static String getClassName(String fullName) {
		String[] tokens = fullName.split(Pattern.quote("."));
		for (int i = 0; i < tokens.length; ++i) {
			if (!tokens[i].isEmpty() && Character.isUpperCase(tokens[i].charAt(0))) {
				 return String.join(".", Arrays.copyOfRange(tokens, i, tokens.length));
			}
		}
		return "";
	}

}

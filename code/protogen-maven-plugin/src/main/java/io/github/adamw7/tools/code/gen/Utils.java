package io.github.adamw7.tools.code.gen;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Utils {

	public static final String IFC_SUFFIX = "Ifc";
	public static final String IMPL_SUFFIX = "Impl";

	private Utils() {}

	public static String to(FieldDescriptor fieldDescriptor, String suffix) {
		return Utils.firstToUpper(fieldDescriptor.getName()) + suffix;
	}

	/**
	 * Recases the first character and the rest independently, which is the whole of
	 * what the three case helpers below differ in. An empty string has neither part
	 * and is returned as it is: a leading, trailing or doubled underscore in a proto
	 * name yields an empty split part, and it contributes nothing to the camel-case
	 * name.
	 */
	private static String recased(String string, UnaryOperator<String> first, UnaryOperator<String> rest) {
		if (string.isEmpty()) {
			return string;
		}
		return first.apply(string.substring(0, 1)) + rest.apply(string.substring(1));
	}

	public static String firstToLower(String string) {
		return recased(string, String::toLowerCase, UnaryOperator.identity());
	}

	public static String firstToUpper(String string) {
		return recased(string, String::toUpperCase, UnaryOperator.identity());
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
		return recased(s, String::toUpperCase, String::toLowerCase);
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
		return joinFrom(tokens, tokens.length - howMany, delimiter);
	}

	public static String getClassName(String fullName) {
		String[] tokens = fullName.split(Pattern.quote("."));
		for (int i = 0; i < tokens.length; ++i) {
			if (startsUpperCase(tokens[i])) {
				return joinFrom(tokens, i, ".");
			}
		}
		return "";
	}

	/** The tokens from {@code first} onwards, rejoined with the delimiter they were split on. */
	private static String joinFrom(String[] tokens, int first, String delimiter) {
		return String.join(delimiter, Arrays.copyOfRange(tokens, first, tokens.length));
	}

	private static boolean startsUpperCase(String token) {
		return !token.isEmpty() && Character.isUpperCase(token.charAt(0));
	}

}

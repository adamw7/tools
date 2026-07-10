package io.github.adamw7.tools.code.gen;

import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;

public class Methods {

	private final TypeMappings typeMappings;
	private final String className;

	public Methods(TypeMappings typeMappings, String className) {
		this.typeMappings = typeMappings;
		this.className = className;
	}

	public StringBuilder setter(FieldDescriptor field, String returnType) {
		String call = isPureRepeated(field) ? "addAll" : "set";
		String fieldName = Utils.toUpperCamelCase(field.getName());
		return override("public %s %s {builder.%s%s(%s);return this;}"
				.formatted(returnType, generateSetter(field), call, fieldName, field.getName()));
	}

	private boolean isPureRepeated(FieldDescriptor field) {
		return field.isRepeated() && !field.isMapField();
	}

	public StringBuilder build() {
		return override("public %s build() {return builder.build();}".formatted(className));
	}

	public StringBuilder requiredSetter(String classOrBuilder, FieldDescriptor field,
			List<FieldDescriptor> requiredFields) {
		String returnType = Utils.getNextIfc(className, requiredFields, field);
		String nextImpl = Utils.getNextImpl(className, requiredFields, field);
		return override("public %s %s {%s%s(%s); return new %s(%s);}".formatted(returnType,
				generateSetter(field), classOrBuilder, builderMethodName(field), field.getName(),
				nextImpl, classOrBuilder));
	}

	private String builderMethodName(FieldDescriptor field) {
		String suffix = Utils.toUpperCamelCase(field.getName());
		String method;
		if (field.isMapField()) {
			method = ".putAll";
		} else if (field.isRepeated()) {
			method = ".addAll";
		} else {
			method = ".set";
		}
		return method + suffix;
	}

	public StringBuilder has(String classOrBuilder, FieldDescriptor field) {
		if (!needsHas(field)) {
			return new StringBuilder();
		}
		String fieldName = Utils.toUpperCamelCase(field.getName());
		return override("public boolean has%s() {return %s.has%s();}"
				.formatted(fieldName, classOrBuilder, fieldName));
	}

	private boolean needsHas(FieldDescriptor field) {
		return field.hasPresence();
	}

	public StringBuilder clear(String classOrBuilder, FieldDescriptor field, String returnType) {
		return clearByName(classOrBuilder, field.getName(), returnType);
	}

	private StringBuilder clearByName(String classOrBuilder, String rawName, String returnType) {
		String name = Utils.toUpperCamelCase(rawName);
		String impl = returnType.replace(Utils.IFC_SUFFIX, Utils.IMPL_SUFFIX);
		return override("public %s clear%s() {%s.clear%s();return new %s(%s);}"
				.formatted(returnType, name, classOrBuilder, name, impl, classOrBuilder));
	}

	public StringBuilder oneofCaseGetter(String classOrBuilder, OneofDescriptor oneof) {
		String name = Utils.toUpperCamelCase(oneof.getName());
		String caseType = "%s.%sCase".formatted(className, name);
		return override("public %s get%sCase() {return %s.get%sCase();}"
				.formatted(caseType, name, classOrBuilder, name));
	}

	public StringBuilder oneofClear(String classOrBuilder, OneofDescriptor oneof, String returnType) {
		return clearByName(classOrBuilder, oneof.getName(), returnType);
	}

	private StringBuilder override(String body) {
		return new StringBuilder("@Override").append(System.lineSeparator()).append(body);
	}

	public StringBuilder constructor(String implName, String classOrBuilder) {
		return new StringBuilder("public %s(Builder %s) {this.%s = %s;}"
				.formatted(implName, classOrBuilder, classOrBuilder, classOrBuilder));
	}

	public StringBuilder declareSetter(FieldDescriptor field, String returnType) {
		return new StringBuilder("%s %s;".formatted(returnType, generateSetter(field)));
	}

	public StringBuilder declareHas(FieldDescriptor field) {
		if (!needsHas(field)) {
			return new StringBuilder();
		}
		return new StringBuilder(
				"boolean has%s();".formatted(Utils.toUpperCamelCase(field.getName())));
	}

	public StringBuilder declareClear(FieldDescriptor field, String returnType) {
		return declareClearByName(field.getName(), returnType);
	}

	private StringBuilder declareClearByName(String rawName, String returnType) {
		return new StringBuilder(
				"%s clear%s();".formatted(returnType, Utils.toUpperCamelCase(rawName)));
	}

	public StringBuilder declareOneofCaseGetter(OneofDescriptor oneof) {
		String name = Utils.toUpperCamelCase(oneof.getName());
		return new StringBuilder(
				"%s.%sCase get%sCase();".formatted(className, name, name));
	}

	public StringBuilder declareOneofClear(OneofDescriptor oneof, String returnType) {
		return declareClearByName(oneof.getName(), returnType);
	}

	private String generateSetter(FieldDescriptor field) {
		return "set%s(%s %s)".formatted(Utils.toUpperCamelCase(field.getName()),
				typeMappings.get(field), field.getName());
	}

}

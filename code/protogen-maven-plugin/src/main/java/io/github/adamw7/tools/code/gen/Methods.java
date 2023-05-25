package io.github.adamw7.tools.code.gen;

import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Methods {

	private final TypeMappings typeMappings;
	private final String className;

	public Methods(TypeMappings typeMappings, String className) {
		this.typeMappings = typeMappings;
		this.className = className;
	}

	public StringBuilder setter(FieldDescriptor field, String returnType) {
		StringBuilder builder = new StringBuilder("@Override\n");
		builder.append("public ").append(returnType).append(" ");
		builder.append(generateSetter(field)).append(" {\n");
		builder.append("builder.");
		if (isPureRepeated(field)) {
			builder.append("addAll");
		} else {
			builder.append("set");
		}
		builder.append(Utils.toUpperCamelCase(field.getName()));
		builder.append("(");
		builder.append(field.getName()).append(");\n");			
		builder.append("return this;\n}\n");

		return builder;
	}

	private boolean isPureRepeated(FieldDescriptor field) {
		return field.isRepeated() && !field.isMapField();
	}

	public StringBuilder build() {
		StringBuilder builder = new StringBuilder();
		builder.append("@Override\n").append("public ").append(className).append(" build() {\n");
		builder.append("return builder.build();\n}");

		return builder;
	}

	public StringBuilder requiredSetter(String classOrBuilder, FieldDescriptor field,
			List<FieldDescriptor> requiredFields) {
		StringBuilder builder = new StringBuilder("@Override\n");
		String returnType = Utils.getNextIfc(className, requiredFields, field);
		builder.append("public ").append(returnType).append(" ").append(generateSetter(field))
				.append(" {\n");
		builder.append("").append(classOrBuilder).append(builderMethodName(field));
		builder.append("(");
		builder.append(field.getName()).append(");\n");
		builder.append("return new ").append(Utils.getNextImpl(className, requiredFields, field)).append("(")
				.append(classOrBuilder).append(");\n");
		builder.append("}\n");

		return builder;
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
		StringBuilder builder = new StringBuilder("@Override\n");
		String fieldName = Utils.toUpperCamelCase(field.getName());
		builder.append("public boolean has").append(fieldName);
		builder.append("() {\n");
		builder.append("return ").append(classOrBuilder).append(".has").append(fieldName).append("();\n");
		builder.append("}\n");

		return builder;
	}

	private boolean needsHas(FieldDescriptor field) {
		return !field.isMapField() && !field.isRepeated();
	}

	public StringBuilder clear(String classOrBuilder, FieldDescriptor field, String returnType) {
		StringBuilder builder = new StringBuilder("@Override\n");
		String fieldName = Utils.toUpperCamelCase(field.getName());
		builder.append("public ").append(returnType);
		builder.append(" clear").append(fieldName);
		builder.append("() {\n");
		builder.append("").append(classOrBuilder).append(".clear").append(fieldName).append("();\n");
		builder.append("return new ").append(returnType.replace("Ifc", "Impl")).append("(").append(classOrBuilder)
				.append(");\n");
		builder.append("}\n");

		return builder;
	}

	public StringBuilder constructor(String implName, String classOrBuilder) {
		StringBuilder builder = new StringBuilder("public ");
		builder.append(implName).append("(Builder ").append(classOrBuilder);
		builder.append(") {\n");
		builder.append("this.").append(classOrBuilder).append(" = ").append(classOrBuilder).append(";\n}\n");
		return builder;
	}

	public StringBuilder declareSetter(FieldDescriptor field, String returnType) {
		StringBuilder builder = new StringBuilder("");
		builder.append(returnType).append(" ");
		builder.append(generateSetter(field));
		builder.append(";\n");

		return builder;
	}

	public StringBuilder declareHas(FieldDescriptor field) {
		if (!needsHas(field)) {
			return new StringBuilder();
		}
		StringBuilder builder = new StringBuilder("boolean has");
		builder.append(Utils.toUpperCamelCase(field.getName()));
		builder.append("();\n");

		return builder;
	}

	public StringBuilder declareClear(FieldDescriptor field, String returnType) {
		StringBuilder builder = new StringBuilder("");
		builder.append(returnType).append(" clear");
		builder.append(Utils.toUpperCamelCase(field.getName()));
		builder.append("();\n");

		return builder;
	}

	private StringBuilder generateSetter(FieldDescriptor field) {
		StringBuilder builder = new StringBuilder("set");
		builder.append(Utils.toUpperCamelCase(field.getName()));
		builder.append("(");
		builder.append(typeMappings.get(field));					
		builder.append(" ").append(field.getName()).append(")");

		return builder;
	}

}

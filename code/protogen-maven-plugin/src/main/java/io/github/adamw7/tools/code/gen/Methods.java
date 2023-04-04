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
		StringBuilder builder = new StringBuilder("\t@Override\n");
		builder.append("\tpublic ").append(returnType).append(" ");
		builder.append(generateSetter(field, typeMappings)).append(" {\n");
		builder.append("\t\tbuilder.set").append(Utils.toUpperCamelCase(field.getName()));
		builder.append("(").append(field.getName()).append(");\n");
		builder.append("\t\treturn this;\n\t}\n");
		
		return builder;
	}

	public StringBuilder build() {
		StringBuilder builder = new StringBuilder();
		builder.append("\t@Override\n").append("\tpublic ").append(className).append(" build() {\n");
		builder.append("\t\treturn builder.build();\n\t}");
		
		return builder;
	}

	public StringBuilder requiredSetter(String classOrBuilder, FieldDescriptor field, List<FieldDescriptor> requiredFields) {
		StringBuilder builder = new StringBuilder("\t@Override\n");
		builder.append("\tpublic ").append(Utils.getNext(className, requiredFields, field, "Ifc")).append(" ")
				.append(generateSetter(field, typeMappings)).append(" {\n");
		builder.append("\t\t").append(classOrBuilder).append(".set").append(Utils.firstToUpper(field.getName()));
		builder.append("(").append(field.getName()).append(");\n");
		builder.append("\t\treturn new ").append(Utils.getNext(className, requiredFields, field, "Impl")).append("(")
				.append(classOrBuilder).append(");\n");
		builder.append("\t}\n");
		
		return builder;
	}
	
	public StringBuilder has(String classOrBuilder, FieldDescriptor field) {
		StringBuilder builder = new StringBuilder("\t@Override\n");
		String fieldName = Utils.toUpperCamelCase(field.getName());
		builder.append("\tpublic boolean has").append(fieldName);
		builder.append("() {\n");
		builder.append("\t\treturn ").append(classOrBuilder).append(".has").append(fieldName).append("();\n");
		builder.append("\t}\n");
		
		return builder;
	}

	public StringBuilder constructor(String implName, String classOrBuilder) {
		StringBuilder builder = new StringBuilder("\tpublic ");
		builder.append(implName).append("(Builder ").append(classOrBuilder);
		builder.append(") {\n");
		builder.append("\t\tthis.").append(classOrBuilder).append(" = ").append(classOrBuilder)
				.append(";\n\t}\n");
		return builder;
	}

	public StringBuilder declareSetter(FieldDescriptor field, String returnType) {
		StringBuilder builder = new StringBuilder("\t");
		builder.append(returnType).append(" ");
		builder.append(generateSetter(field, typeMappings));
		builder.append(";\n");
		
		return builder;
	}
	
	public StringBuilder declareHas(FieldDescriptor field) {
		StringBuilder builder = new StringBuilder("\tboolean has");
		builder.append(Utils.toUpperCamelCase(field.getName()));
		builder.append("();\n");
		
		return builder;
	}

	private static StringBuilder generateSetter(FieldDescriptor field, TypeMappings mappings) {
		StringBuilder builder = new StringBuilder("set");
		builder.append(Utils.toUpperCamelCase(field.getName()));
		builder.append("(").append(mappings.get(field));
		builder.append(" ").append(field.getName()).append(")");
		
		return builder;
	}

}

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
		StringBuilder builder = new StringBuilder("\t\t@Override\n");
		builder.append("\t\tpublic ").append(returnType).append(" ");
		builder.append(Utils.generateSetter(field, typeMappings)).append(" {\n");
		builder.append("\t\t\tbuilder.set").append(Utils.firstToUpper(field.getName()));
		builder.append("(").append(field.getName()).append(");\n");
		builder.append("\t\t\treturn this;\n\t\t}\n");
		return builder;
	}

	public StringBuilder build() {
		StringBuilder builder = new StringBuilder();
		builder.append("\t@Override\n").append("\t\tpublic ").append(className).append(" build() {\n");
		builder.append("\t\treturn builder.build();\n\t\t}");
		return builder;
	}

	public StringBuilder requiredSetter(String classOrBuilder, FieldDescriptor field, List<FieldDescriptor> requiredFields) {
		StringBuilder builder = new StringBuilder("\t@Override\n");
		builder.append("\tpublic ").append(Utils.getNext(requiredFields, field, "Ifc")).append(" ")
				.append(Utils.generateSetter(field, typeMappings)).append(" {\n");
		builder.append("\t\t").append(classOrBuilder).append(".set").append(Utils.firstToUpper(field.getName()));
		builder.append("(").append(field.getName()).append(");\n");
		builder.append("\t\treturn new ").append(Utils.getNext(requiredFields, field, "Impl")).append("(")
				.append(classOrBuilder).append(");\n");
		builder.append("\t}");
		return builder;
	}

	public StringBuilder constructor(String implName, String classOrBuilder) {
		StringBuilder builder = new StringBuilder();
		builder.append("\tpublic ").append(implName).append("(Builder ").append(classOrBuilder);
		builder.append(") {\n");
		builder.append("\t\tthis.").append(classOrBuilder).append(" = ").append(classOrBuilder)
				.append(";\n\t}\n");
		return builder;
	}

}

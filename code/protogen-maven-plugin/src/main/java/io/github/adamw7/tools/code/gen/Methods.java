package io.github.adamw7.tools.code.gen;

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
}

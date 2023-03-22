package io.github.adamw7.tools.code.gen;

import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Implementations {

	private final String className;
	private final List<FieldDescriptor> optionalFields;
	private final List<FieldDescriptor> requiredFields;
	private final TypeMappings typeMappings;

	public Implementations(String className, List<FieldDescriptor> optionalFields, List<FieldDescriptor> requiredFields,
			TypeMappings typeMappings) {
		this.className = className;
		this.optionalFields = optionalFields;
		this.requiredFields = requiredFields;
		this.typeMappings = typeMappings;
	}

	public StringBuilder generateRequired() {
		StringBuilder builder = new StringBuilder();
		if (requiredFields.size() > 1) {
			for (int i = 1; i < requiredFields.size(); ++i) { // skipping first since already handled
				String classOrBuilder = Utils.firstToLower(className) + "OrBuilder";
				FieldDescriptor field = requiredFields.get(i);
				String ifcName = Utils.firstToUpper(field.getName()) + "Ifc";
				String implName = Utils.firstToUpper(field.getName()) + "Impl";
				builder.append("class ").append(implName).append(" implements ").append(ifcName).append(" {\n");
				builder.append("\tprivate final Builder ").append(classOrBuilder).append(";\n");
				builder.append("\tpublic ").append(implName).append("(Builder ").append(classOrBuilder);
				builder.append(") {\n");
				builder.append("\t\tthis.").append(classOrBuilder).append(" = ").append(classOrBuilder)
						.append(";\n\t}\n");
				builder.append(generateRequiredSetter(classOrBuilder, field));
				builder.append("\n}\n");
			}
		}
		return builder;
	}

	private StringBuilder generateRequiredSetter(String classOrBuilder, FieldDescriptor field) {
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

	public StringBuilder generateOptional() {
		StringBuilder builder = new StringBuilder("class OptionalImpl implements OptionalIfc {\n");
		builder.append("\tprivate final Builder builder;\n");
		builder.append("\tpublic OptionalImpl(Builder builder) {\n");
		builder.append("\t\tthis.builder = builder;\n\t\t}\n");
		builder.append(generateSetters());
		builder.append("\t@Override\n").append("\t\tpublic ").append(className).append(" build() {\n");
		builder.append("\t\treturn builder.build();\n\t\t}\n\t}\n");
		return builder;
	}

	private StringBuilder generateSetters() {
		StringBuilder builder = new StringBuilder();
		for (FieldDescriptor field : optionalFields) {
			builder.append("\t\t@Override\n");
			builder.append("\t\tpublic OptionalIfc ");
			builder.append(Utils.generateSetter(field, typeMappings)).append(" {\n");
			builder.append("\t\t\tbuilder.set").append(Utils.firstToUpper(field.getName()));
			builder.append("(").append(field.getName()).append(");\n");
			builder.append("\t\t\treturn this;\n\t\t}\n");
		}
		return builder;
	}
}

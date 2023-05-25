package io.github.adamw7.tools.code.gen;

import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Implementations extends AbstractStatements {

	public Implementations(String className, List<FieldDescriptor> optionalFields, List<FieldDescriptor> requiredFields,
			List<FieldDescriptor> mapFields, List<FieldDescriptor> repeatedFields, TypeMappings typeMappings) {
		super(className, optionalFields, requiredFields, mapFields, repeatedFields, typeMappings);
	}

	public StringBuilder generateRequired() {
		StringBuilder builder = new StringBuilder();
		if (nonOptionalFields.size() > 1) {
			for (int i = 1; i < nonOptionalFields.size(); ++i) { // skipping first since already handled
				String classOrBuilder = Utils.firstToLower(className) + "OrBuilder";
				FieldDescriptor field = nonOptionalFields.get(i);
				String ifcName = Utils.firstToUpper(field.getName()) + "Ifc";
				String implName = Utils.firstToUpper(field.getName()) + "Impl";
				builder.append("class ").append(implName).append(" implements ").append(ifcName).append(" {");
				builder.append("private final Builder ").append(classOrBuilder).append(";");
				builder.append(methods.constructor(implName, classOrBuilder));
				builder.append(methods.requiredSetter(classOrBuilder, field, nonOptionalFields));
				builder.append(methods.has(classOrBuilder, field));	
				String clearReturnType = Utils.getNextIfc(className, nonOptionalFields, field);
				builder.append(methods.clear(classOrBuilder, field, clearReturnType));					
				builder.append("}");
			}
		}
		return builder;
	}

	public StringBuilder generateOptional() {
		StringBuilder builder = new StringBuilder("class ");
		builder.append(optionalImplName);
		builder.append(" implements ");
		builder.append(optionalIfcName);
		builder.append(" {");
		builder.append(generateOptionalBuilderField());
		builder.append(generateOptionalBuilderConstructor(optionalImplName));
		builder.append(generateMethods());
		builder.append(methods.build());
		builder.append("}");
		return builder;
	}

	public StringBuilder generateOptionalBuilderConstructor(String name) {
		StringBuilder builder = new StringBuilder();
		builder.append("public ").append(name);
		builder.append("(Builder builder) {");
		builder.append("this.builder = builder;}");
		return builder;
	}

	public StringBuilder generateMethods() {
		StringBuilder builder = new StringBuilder();
		for (FieldDescriptor field : optionalFields) {
			builder.append(methods.setter(field, optionalIfcName));
			builder.append("");
			builder.append(methods.has("builder", field));
			builder.append(methods.clear("builder", field, optionalIfcName));	
		}
		return builder;
	}

	public String generateOptionalBuilderField() {
		return "private final Builder builder;";
	}
}

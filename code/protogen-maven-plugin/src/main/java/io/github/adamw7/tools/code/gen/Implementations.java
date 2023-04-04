package io.github.adamw7.tools.code.gen;

import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Implementations {

	private final String className;
	private final List<FieldDescriptor> optionalFields;
	private final List<FieldDescriptor> requiredFields;
	private final Methods methods;
	private final String optionalImplName;
	private final String optionalIfcName;

	public Implementations(String className, List<FieldDescriptor> optionalFields, List<FieldDescriptor> requiredFields,
			TypeMappings typeMappings) {
		this.className = className;
		this.optionalFields = optionalFields;
		this.requiredFields = requiredFields;
		methods = new Methods(typeMappings, className);
		this.optionalIfcName = className + "OptionalIfc";
		this.optionalImplName = className + "OptionalImpl";		
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
				builder.append(methods.constructor(implName, classOrBuilder));
				builder.append(methods.requiredSetter(classOrBuilder, field, requiredFields));
				builder.append(methods.has(classOrBuilder, field));				
				builder.append("\n}\n");
			}
		}
		return builder;
	}

	public StringBuilder generateOptional() {
		StringBuilder builder = new StringBuilder("class ");
		builder.append(optionalImplName);
		builder.append(" implements ");
		builder.append(optionalIfcName);
		builder.append(" {\n");
		builder.append("\tprivate final Builder builder;\n\n");
		builder.append("\tpublic ").append(optionalImplName);
		builder.append("(Builder builder) {\n");
		builder.append("\t\tthis.builder = builder;\n\t}\n");
		builder.append(generateMethods());
		builder.append(methods.build());
		builder.append("\n}\n");
		return builder;
	}

	private StringBuilder generateMethods() {
		StringBuilder builder = new StringBuilder();
		for (FieldDescriptor field : optionalFields) {
			builder.append(methods.setter(field, optionalIfcName));
			builder.append("\n");
			builder.append(methods.has("builder", field));			
		}
		return builder;
	}
}

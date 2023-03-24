package io.github.adamw7.tools.code.gen;

import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Interfaces {
	private final String className;
	private final List<FieldDescriptor> optionalFields;
	private final List<FieldDescriptor> requiredFields;
	private final Methods methods;

	public Interfaces(String className, List<FieldDescriptor> optionalFields, List<FieldDescriptor> requiredFields, TypeMappings typeMappings) {
		this.className = className;
		this.optionalFields = optionalFields;
		this.requiredFields = requiredFields;
		this.methods = new Methods(typeMappings, className);
	}

	public StringBuilder generateRequired() {
		StringBuilder interfaces = new StringBuilder();
		
		for (FieldDescriptor requiredField : requiredFields) {
			interfaces.append(generateInterface(requiredField)).append("\n");
		}
		return interfaces;
	}
	
	public StringBuilder generateOptional() {
		StringBuilder builder = new StringBuilder("interface OptionalIfc {\n");
		
		for (FieldDescriptor optionalField : optionalFields) {
			builder.append(methods.declareSetter(optionalField, "OptionalIfc"));
		}
		
		builder.append("\t").append(className).append(" build();\n");
		builder.append("}\n");
		
		return builder;
	}
	
	private StringBuilder generateInterface(FieldDescriptor requiredField) {
		StringBuilder ifc = new StringBuilder();
		ifc.append("interface ");
		ifc.append(Utils.to(requiredField, "Ifc"));
		ifc.append(" {").append("\n");
		ifc.append(methods.declareSetter(requiredField, Utils.getNext(requiredFields, requiredField, "Ifc")));
		ifc.append(methods.declareHas(requiredField));
		
		ifc.append("\n}");
				
		return ifc;
	}
}

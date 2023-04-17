package io.github.adamw7.tools.code.gen;

import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Interfaces {
	private final String className;
	private final List<FieldDescriptor> optionalFields;
	private final List<FieldDescriptor> requiredFields;
	private final Methods methods;
	private final String optionalIfcName;

	public Interfaces(String className, List<FieldDescriptor> optionalFields, List<FieldDescriptor> requiredFields, TypeMappings typeMappings) {
		this.className = className;
		this.optionalFields = optionalFields;
		this.requiredFields = requiredFields;
		this.methods = new Methods(typeMappings, className);
		optionalIfcName = className + "OptionalIfc";
	}

	public StringBuilder generateRequired() {
		StringBuilder interfaces = new StringBuilder();
		
		for (FieldDescriptor requiredField : requiredFields) {
			interfaces.append(generateInterface(requiredField)).append("\n");
		}
		return interfaces;
	}
	
	public StringBuilder generateOptional() {
		StringBuilder builder = new StringBuilder("interface ");
		builder.append(optionalIfcName).append(" {\n");
		
		for (FieldDescriptor optionalField : optionalFields) {
			builder.append(methods.declareSetter(optionalField, optionalIfcName));
			builder.append(methods.declareHas(optionalField));	
			builder.append(methods.declareClear(optionalField, Utils.getNext(className, requiredFields, optionalField, "Ifc")));
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
		String returnType = Utils.getNext(className, requiredFields, requiredField, "Ifc");
		ifc.append(methods.declareSetter(requiredField, returnType));
		ifc.append(methods.declareHas(requiredField));
		ifc.append(methods.declareClear(requiredField, returnType));		
		
		ifc.append("\n}");
				
		return ifc;
	}
}

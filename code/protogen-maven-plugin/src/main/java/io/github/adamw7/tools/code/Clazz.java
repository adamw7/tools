package io.github.adamw7.tools.code;

import static java.lang.System.lineSeparator;

import java.util.List;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class Clazz {

	private final Descriptor descriptor;
	public static final String PKG = "io.github.adamw7.tools.code";
	private final String className;
	private final TypeMappings typeMappings;
	private final List<FieldDescriptor> requiredFields;
	 
	public Clazz(Descriptor descriptor, TypeMappings typeMappings) {
		this.descriptor = descriptor;
		className = getClassName();
		this.typeMappings = typeMappings;
		requiredFields = getRequiredFields();
	}

	private List<FieldDescriptor> getRequiredFields() {
		return descriptor.getFields().stream().filter(f -> f.isRequired()).toList();		
	}

	private String getClassName() {
		return descriptor.getName();
	}

	public String generate() {
		StringBuilder pkg = generatePackage();
		StringBuilder imports = generateImports();
		StringBuilder header = generateHeader();
		StringBuilder fields = generateFields();
		StringBuilder interfaces = generateInterfaces();		
		StringBuilder footer = generateFooter();
		
		StringBuilder full = new StringBuilder();		
		full.append(pkg).append(imports).append(header).append(fields).append(interfaces).append(footer);
		return full.toString();
	}

	private StringBuilder generateInterfaces() {
		StringBuilder interfaces = new StringBuilder("");
		
		for (FieldDescriptor requiredField : requiredFields) {
			interfaces.append(generateInteface(requiredField)).append("\n");
		}
		return interfaces;
	}

	private StringBuilder generateInteface(FieldDescriptor requiredField) {
		StringBuilder ifc = new StringBuilder("");
		ifc.append("static interface ");
		ifc.append(firstToUpper(requiredField.getName()));
		ifc.append("Ifc {").append("\n\t");
		ifc.append(getNextIfc()).append(" ");
		ifc.append("set").append(firstToUpper(requiredField.getName()));
		ifc.append("(").append(typeMappings.get(requiredField));
		ifc.append(" ").append(requiredField.getName()).append(");");
		ifc.append("\n").append("}");
				
		return ifc;
	}

	private String firstToUpper(String string) {
		return (String.valueOf(string.charAt(0)).toUpperCase() + string.substring(1, string.length()));
	}

	private StringBuilder getNextIfc() {
		return new StringBuilder("DepartmentIfc");
	}

	private StringBuilder generatePackage() {
		return new StringBuilder("package ").append(PKG).append(";").append(lineSeparator());
	}

	private StringBuilder generateFields() {
		List<FieldDescriptor> fields = descriptor.getFields();
		StringBuilder builder = new StringBuilder();
		for (FieldDescriptor field : fields) {
			builder.append(generateField(field));
			builder.append(lineSeparator());
		}
		return builder;
	}

	private StringBuilder generateField(FieldDescriptor field) {
		StringBuilder builder = new StringBuilder();
		builder.append("private ");
		builder.append(getTypeName(field));
		builder.append(" ");
		builder.append(field.getName());
		builder.append(";");
		return builder;
	}

	private String getTypeName(FieldDescriptor field) {
		return typeMappings.get(field);
	}

	private StringBuilder generateFooter() {
		return new StringBuilder("}").append(lineSeparator());
	}

	private StringBuilder generateHeader() {
		StringBuilder builder = new StringBuilder("public class ").append(className).append("Builder");
		builder.append(" {").append(lineSeparator());
		return builder;
	}

	private StringBuilder generateImports() {
		return new StringBuilder();
	}

}

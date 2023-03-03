package io.github.adamw7.tools.code;

import static java.lang.System.lineSeparator;

import java.util.List;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class Clazz {

	private final Descriptor descriptor;
	public static final String OUTPUT_PKG = "io.github.adamw7.tools.code";
	private final String className;
	private final TypeMappings typeMappings;
	private final List<FieldDescriptor> requiredFields;
	private final List<FieldDescriptor> optionalFields;	
	private final String pkg;
	 
	public Clazz(Descriptor descriptor, TypeMappings typeMappings, Package pkg) {
		this.descriptor = descriptor;
		className = getClassName();
		this.typeMappings = typeMappings;
		requiredFields = getRequiredFields();
		optionalFields = getOptionalFields();
		this.pkg = pkg.getName();		
	}

	private List<FieldDescriptor> getRequiredFields() {
		return descriptor.getFields().stream().filter(FieldDescriptor::isRequired).toList();
	}
	
	private List<FieldDescriptor> getOptionalFields() {
		return descriptor.getFields().stream().filter(FieldDescriptor::isOptional).toList();
	}

	private String getClassName() {
		return descriptor.getName();
	}

	public String generate() {
		StringBuilder pkg = generatePackage();
		StringBuilder imports = generateImports();
		StringBuilder header = generateHeader();
		StringBuilder fields = generateFields();
		StringBuilder requiredInterfaces = generateRequiredInterfaces();	
		StringBuilder optionalInterface = generateOptionalInterface();	
		StringBuilder optionalImpl = generateOptionalImpl();	
		
		StringBuilder footer = generateFooter();
		
		StringBuilder full = new StringBuilder();		
		full.append(pkg).append(imports).append(header).append(fields).append(optionalInterface);
		full.append(optionalImpl).append(requiredInterfaces).append(footer);
		return full.toString();
	}

	private StringBuilder generateOptionalImpl() {
		StringBuilder builder = new StringBuilder("\tstatic class OptionalImpl implements OptionalIfc {\n");
		builder.append("\t\tprivate final Builder builder;\n");
		builder.append("\t\tpublic OptionalImpl(Builder builder) {\n");
		builder.append("\t\t\tthis.builder = builder;\n\t\t}\n");
		builder.append(generateSetters());
		builder.append("\t\t@Override\n").append("\t\tpublic ").append(className).append(" build() {\n");
		builder.append("\t\t\treturn builder.build();\n\t\t}\n\t}\n");
		return builder;
	}

	private StringBuilder generateSetters() {
		StringBuilder builder = new StringBuilder();
		for (FieldDescriptor field : optionalFields) {
			builder.append("\t\t@Override\n");
			builder.append("\t\tpublic OptionalIfc ");
			builder.append(generateSetter(field)).append(" {\n");
			builder.append("\t\t\tbuilder.set").append(firstToUpper(field.getName()));
			builder.append("(").append(field.getName()).append(");\n");
			builder.append("\t\t\treturn this;\n\t\t}\n");
		}
		return builder;
	}

	private StringBuilder generateOptionalInterface() {
		StringBuilder builder = new StringBuilder("\tstatic interface OptionalIfc {\n");
		
		for (FieldDescriptor optionalField : optionalFields) {
			builder.append("\t\tOptionalIfc ");
			builder.append(generateSetter(optionalField));
			builder.append(";\n");
		}
		
		builder.append("\t\t").append(className).append(" build();\n");
		builder.append("\t}\n");
		
		return builder;
	}

	private StringBuilder generateSetter(FieldDescriptor field) {
		StringBuilder builder = new StringBuilder("set");
		builder.append(firstToUpper(field.getName()));
		builder.append("(").append(typeMappings.get(field));
		builder.append(" ").append(field.getName()).append(")");
		return builder;
	}

	private StringBuilder generateRequiredInterfaces() {
		StringBuilder interfaces = new StringBuilder();
		
		for (FieldDescriptor requiredField : requiredFields) {
			interfaces.append(generateInterface(requiredField)).append("\n");
		}
		return interfaces;
	}

	private StringBuilder generateInterface(FieldDescriptor requiredField) {
		StringBuilder ifc = new StringBuilder();
		ifc.append("\tstatic interface ");
		ifc.append(firstToUpper(requiredField.getName()));
		ifc.append("Ifc {").append("\n\t\t");
		ifc.append(getNextIfc(requiredField)).append(" ");
		ifc.append(generateSetter(requiredField));
		ifc.append(";\n\t}");
				
		return ifc;
	}

	private String firstToUpper(String string) {
		return (String.valueOf(string.charAt(0)).toUpperCase() + string.substring(1));
	}

	private String getNextIfc(FieldDescriptor requiredField) {
		for (int i = 0; i < requiredFields.size(); ++i) {
			if (requiredFields.get(i).equals(requiredField)) {
				return i == requiredFields.size() - 1 ? "OptionalIfc" : firstToUpper(requiredFields.get(i + 1).getName()) + "Ifc";
			}
		}
		return "OptionalIfc";
	}

	private StringBuilder generatePackage() {
		return new StringBuilder("package ").append(OUTPUT_PKG).append(";").append(lineSeparator());
	}

	private StringBuilder generateFields() {
		StringBuilder builder = new StringBuilder("\tprivate final ");
		builder.append(className).append(".Builder ");
		
		builder.append(className.toLowerCase()).append("Builder = ");
		builder.append(className).append(".newBuilder();\n");
		return builder;
	}

	private StringBuilder generateFooter() {
		return new StringBuilder("}").append(lineSeparator());
	}

	private StringBuilder generateHeader() {
		StringBuilder builder = new StringBuilder("public class ").append(className).append("Builder implements ");
		builder.append(firstInterface());
		builder.append(" {").append(lineSeparator());
		return builder;
	}

	private String firstInterface() {
		return requiredFields.size() == 0 ? "OptionalIfc" : firstToUpper(requiredFields.get(0).getName()) + "Ifc";
	}

	private StringBuilder generateImports() {
		return new StringBuilder("import ").append(pkg).append(";\n");
	}

}

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
	 
	public Clazz(Descriptor descriptor, TypeMappings typeMappings) {
		this.descriptor = descriptor;
		className = getClassName();
		this.typeMappings = typeMappings;
	}

	private String getClassName() {
		return descriptor.getName();
	}

	public String generate() {
		StringBuilder pkg = generatePackage();
		StringBuilder imports = generateImports();
		StringBuilder header = generateHeader();
		StringBuilder fields = generateFields();
		StringBuilder footer = generateFooter();
		
		StringBuilder full = new StringBuilder();		
		full.append(pkg).append(imports).append(header).append(fields).append(footer);
		return full.toString();
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

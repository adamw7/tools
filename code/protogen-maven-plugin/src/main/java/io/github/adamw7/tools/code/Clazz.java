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
		StringBuilder requiredInterfaces = generateRequiredInterfaces();	
		StringBuilder optionalInterface = generateOptionalInterface();
		StringBuilder requiredImpl = generateRequiredImpl();
		
		StringBuilder header = generateHeader();
		StringBuilder fields = generateFields();	
		StringBuilder optionalImpl = generateOptionalImpl();	
		
		StringBuilder footer = generateFooter();
		
		StringBuilder full = new StringBuilder();		
		full.append(pkg).append(imports).append(optionalInterface).append(requiredInterfaces).append(requiredImpl);
		full.append(optionalImpl).append(header).append(fields);
		full.append(requiredSetter()).append(footer);
		return full.toString();
	}

	private StringBuilder requiredSetter() {
		if (requiredFields.size() == 0) {
			return new StringBuilder();
		} else {
			StringBuilder setter = new StringBuilder("\t@Override\n\tpublic ");
			FieldDescriptor field = requiredFields.get(0);
			setter.append(getNext(field, "Ifc")).append(" ");
			setter.append(generateSetter(field).append(" {\n"));
			setter.append("\t\tpersonBuilder.set").append(firstToUpper(field.getName())).append("(");
			setter.append(field.getName()).append(");\n");
			setter.append("\t\treturn new ").append(getNext(field, "Impl")).append("(personBuilder);\n\t}\n");
			return setter;
		}
	}
	
	private StringBuilder generateRequiredImpl() {
		StringBuilder builder = new StringBuilder();
		if (requiredFields.size() > 1) {
			for (int i = 1; i < requiredFields.size(); ++i) { // skipping first since already handled
				String classOrBuilder = firstToLower(className) + "OrBuilder";
				FieldDescriptor field = requiredFields.get(i);
				String ifcName = firstToUpper(field.getName()) + "Ifc";
				String implName = firstToUpper(field.getName()) + "Impl";
				builder.append("class ").append(implName).append(" implements ").append(ifcName).append(" {\n");
				builder.append("\tprivate final Builder ").append(classOrBuilder).append(";\n");
				builder.append("\tpublic ").append(implName).append("(Builder ").append(classOrBuilder);
				builder.append(") {\n");
				builder.append("\t\tthis.").append(classOrBuilder).append(" = ").append(classOrBuilder).append(";\n\t}\n");
				builder.append(generateRequiredSetter(classOrBuilder, field));
				builder.append("\n}\n");
			}
		}
		return builder;
	}

	private StringBuilder generateRequiredSetter(String classOrBuilder, FieldDescriptor field) {
		StringBuilder builder = new StringBuilder("\t@Override\n");
		builder.append("\tpublic ").append(getNext(field, "Ifc")).append(" ").append(generateSetter(field)).append(" {\n");
		builder.append("\t\t").append(classOrBuilder).append(".set").append(firstToUpper(field.getName()));
		builder.append("(").append(field.getName()).append(");\n");
		builder.append("\t\treturn new ").append(getNext(field, "Impl")).append("(").append(classOrBuilder).append(");\n");
		builder.append("\t}");
		return builder;
	}

	private StringBuilder generateOptionalImpl() {
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
			builder.append(generateSetter(field)).append(" {\n");
			builder.append("\t\t\tbuilder.set").append(firstToUpper(field.getName()));
			builder.append("(").append(field.getName()).append(");\n");
			builder.append("\t\t\treturn this;\n\t\t}\n");
		}
		return builder;
	}

	private StringBuilder generateOptionalInterface() {
		StringBuilder builder = new StringBuilder("interface OptionalIfc {\n");
		
		for (FieldDescriptor optionalField : optionalFields) {
			builder.append("\tOptionalIfc ");
			builder.append(generateSetter(optionalField));
			builder.append(";\n");
		}
		
		builder.append("\t").append(className).append(" build();\n");
		builder.append("}\n");
		
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
		ifc.append("interface ");
		ifc.append(to(requiredField, "Ifc"));
		ifc.append(" {").append("\n\t");
		ifc.append(getNext(requiredField, "Ifc")).append(" ");
		ifc.append(generateSetter(requiredField));
		ifc.append(";\n}");
				
		return ifc;
	}

	private String firstToUpper(String string) {
		return (String.valueOf(string.charAt(0)).toUpperCase() + string.substring(1));
	}
	
	private String firstToLower(String string) {
		return (String.valueOf(string.charAt(0)).toLowerCase() + string.substring(1));
	}

	private String getNext(FieldDescriptor requiredField, String suffix) {
		for (int i = 0; i < requiredFields.size(); ++i) {
			if (requiredFields.get(i).equals(requiredField)) {
				return i == requiredFields.size() - 1 ? "Optional" + suffix : to(requiredFields.get(i + 1), suffix);
			}
		}
		return "Optional" + suffix;
	}

	private String to(FieldDescriptor fieldDescriptor, String suffix) {
		return firstToUpper(fieldDescriptor.getName()) + suffix;
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
		return requiredFields.size() == 0 ? "OptionalIfc" : to(requiredFields.get(0), "Ifc");
	}

	private StringBuilder generateImports() {
		StringBuilder builder = new StringBuilder();
		StringBuilder prefix = new StringBuilder("import ").append(pkg).append(".").append(className);
		builder.append(prefix).append(";\n");
		builder.append(prefix).append(".Builder").append(";\n");		
		return builder;
	}

}

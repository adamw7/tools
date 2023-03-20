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
	private final Interfaces interfaces;
	 
	public Clazz(Descriptor descriptor, TypeMappings typeMappings, Package pkg) {
		this.descriptor = descriptor;
		className = getClassName();
		requiredFields = getRequiredFields();
		optionalFields = getOptionalFields();
		this.pkg = pkg.getName();	
		this.typeMappings = typeMappings;
		this.interfaces = new Interfaces(className, optionalFields, requiredFields, typeMappings);
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
		StringBuilder requiredInterfaces = interfaces.generateRequired();	
		StringBuilder optionalInterface = interfaces.generateOptional();
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
			setter.append(Utils.getNext(requiredFields, field, "Ifc")).append(" ");
			setter.append(Utils.generateSetter(field, typeMappings).append(" {\n"));
			setter.append("\t\tpersonBuilder.set").append(Utils.firstToUpper(field.getName())).append("(");
			setter.append(field.getName()).append(");\n");
			setter.append("\t\treturn new ").append(Utils.getNext(requiredFields, field, "Impl")).append("(personBuilder);\n\t}\n");
			return setter;
		}
	}
	
	private StringBuilder generateRequiredImpl() {
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
				builder.append("\t\tthis.").append(classOrBuilder).append(" = ").append(classOrBuilder).append(";\n\t}\n");
				builder.append(generateRequiredSetter(classOrBuilder, field));
				builder.append("\n}\n");
			}
		}
		return builder;
	}

	private StringBuilder generateRequiredSetter(String classOrBuilder, FieldDescriptor field) {
		StringBuilder builder = new StringBuilder("\t@Override\n");
		builder.append("\tpublic ").append(Utils.getNext(requiredFields, field, "Ifc")).append(" ").append(Utils.generateSetter(field, typeMappings)).append(" {\n");
		builder.append("\t\t").append(classOrBuilder).append(".set").append(Utils.firstToUpper(field.getName()));
		builder.append("(").append(field.getName()).append(");\n");
		builder.append("\t\treturn new ").append(Utils.getNext(requiredFields, field, "Impl")).append("(").append(classOrBuilder).append(");\n");
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
			builder.append(Utils.generateSetter(field, typeMappings)).append(" {\n");
			builder.append("\t\t\tbuilder.set").append(Utils.firstToUpper(field.getName()));
			builder.append("(").append(field.getName()).append(");\n");
			builder.append("\t\t\treturn this;\n\t\t}\n");
		}
		return builder;
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
		return requiredFields.size() == 0 ? "OptionalIfc" : Utils.to(requiredFields.get(0), "Ifc");
	}

	private StringBuilder generateImports() {
		StringBuilder builder = new StringBuilder();
		StringBuilder prefix = new StringBuilder("import ").append(pkg).append(".").append(className);
		builder.append(prefix).append(";\n");
		builder.append(prefix).append(".Builder").append(";\n");		
		return builder;
	}

}

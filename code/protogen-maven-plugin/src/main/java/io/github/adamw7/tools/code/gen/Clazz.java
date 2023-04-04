package io.github.adamw7.tools.code.gen;

import static java.lang.System.lineSeparator;

import java.util.List;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class Clazz {

	private final Descriptor descriptor;
	public static final String OUTPUT_PKG = "io.github.adamw7.tools.code";
	private final String className;
	private final List<FieldDescriptor> requiredFields;
	private final String pkg;
	private final Interfaces interfaces;
	private final Implementations implementations;
	private final Methods methods;
	 
	public Clazz(Descriptor descriptor, TypeMappings typeMappings, Package pkg) {
		this.descriptor = descriptor;
		className = getClassName();
		requiredFields = getRequiredFields();
		List<FieldDescriptor> optionalFields = getOptionalFields();
		this.pkg = pkg.getName();	
		this.interfaces = new Interfaces(className, optionalFields, requiredFields, typeMappings);
		this.implementations = new Implementations(className, optionalFields, requiredFields, typeMappings);
		this.methods = new Methods(typeMappings, className);
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
		StringBuilder requiredImpl = implementations.generateRequired();
		
		StringBuilder header = generateHeader();
		StringBuilder fields = generateFields();	
		StringBuilder optionalImpl = implementations.generateOptional();	
		
		StringBuilder footer = generateFooter();
		
		StringBuilder full = new StringBuilder();		
		full.append(pkg).append(imports).append(optionalInterface).append(requiredInterfaces).append(requiredImpl);
		full.append(optionalImpl).append(header).append(fields);
		full.append(requiredSetter()).append(requiredHas()).append(footer);
		return full.toString();
	}

	private StringBuilder requiredSetter() {
		if (requiredFields.size() == 0) {
			return new StringBuilder();
		} else {
			return methods.requiredSetter(Utils.firstToLower(className) + "Builder", requiredFields.get(0), requiredFields);
		}
	}
	
	private StringBuilder requiredHas() {
		if (requiredFields.size() == 0) {
			return new StringBuilder();
		} else {
			return methods.has(Utils.firstToLower(className) + "Builder", requiredFields.get(0));
		}
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
		return requiredFields.size() == 0 ? className + "OptionalIfc" : Utils.to(requiredFields.get(0), "Ifc");
	}

	private StringBuilder generateImports() {
		StringBuilder builder = new StringBuilder();
		StringBuilder prefix = new StringBuilder("import ").append(pkg).append(".").append(className);
		builder.append(prefix).append(";\n");
		builder.append(prefix).append(".Builder").append(";\n");		
		return builder;
	}

}

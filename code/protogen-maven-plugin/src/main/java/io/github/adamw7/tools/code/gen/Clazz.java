package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class Clazz {

	public static final String OUTPUT_PKG = "io.github.adamw7.tools.code";
	private final String className;
	private final List<FieldDescriptor> requiredFields;
	private final String pkg;
	private final Interfaces interfaces;
	private final Implementations implementations;
	private final Methods methods;
	 
	public Clazz(Descriptor descriptor, TypeMappings typeMappings, Package pkg) {
		className = getClassName(descriptor);
		requiredFields = getRequiredFields(descriptor);
		List<FieldDescriptor> mapFields = getMapFields(descriptor);
		List<FieldDescriptor> optionalFields = getOptionalFields(descriptor);
		List<FieldDescriptor> repeatedFields = getRepeatedFields(descriptor);
		
		this.pkg = pkg.getName();	
		String header = generatePackage().append(generateImports()).toString();
		this.interfaces = new Interfaces(className, optionalFields, requiredFields, mapFields, repeatedFields, typeMappings, header);
		this.implementations = new Implementations(className, optionalFields, requiredFields, mapFields, repeatedFields, typeMappings, header);
		this.methods = new Methods(typeMappings, className);
	}

	private List<FieldDescriptor> getRepeatedFields(Descriptor descriptor) {
		return descriptor.getFields().stream().filter(f -> f.isRepeated() && !f.isMapField()).toList();
	}

	private List<FieldDescriptor> getMapFields(Descriptor descriptor) {
		return descriptor.getFields().stream().filter(FieldDescriptor::isMapField).toList();
	}

	private List<FieldDescriptor> getRequiredFields(Descriptor descriptor) {
		return descriptor.getFields().stream().filter(FieldDescriptor::isRequired).toList();
	}
	
	private List<FieldDescriptor> getOptionalFields(Descriptor descriptor) {
		return descriptor.getFields().stream().filter(FieldDescriptor::isOptional).toList();
	}

	private String getClassName(Descriptor descriptor) {
		return descriptor.getName();
	}

	public List<ClassContainer> generate() {
		List<ClassContainer> classes = new ArrayList<>();
		StringBuilder pkg = generatePackage();
		StringBuilder imports = generateImports();
		List<ClassContainer> requiredInterfaces = interfaces.generateRequired();	
		List<ClassContainer> optionalInterface = interfaces.generateOptional();
		List<ClassContainer> requiredImpl = implementations.generateRequired();
		
		StringBuilder header = generateHeader();
		List<ClassContainer> optionalImpl = implementations.generateOptional();	
		String optionalImplInMainClass = handleMethodsInMainClass();
				
		StringBuilder footer = generateFooter();
		
		StringBuilder full = new StringBuilder();		
		full.append(pkg).append(imports);
		full.append(header).append(optionalImplInMainClass);
		full.append(footer);
		
		classes.addAll(optionalInterface);
		classes.addAll(requiredInterfaces);
		classes.addAll(optionalImpl);
		classes.addAll(requiredImpl);
		classes.add(new ClassContainer(className + "Builder", full.toString()));
				
		return classes;
	}

	private String handleMethodsInMainClass() {
		StringBuilder builder = new StringBuilder();
		if (requiredFields.isEmpty()) {
			builder.append(implementations.generateOptionalBuilderField());
			builder.append(implementations.generateOptionalBuilderConstructor(className + "Builder"));
			builder.append(implementations.generateMethods());
			builder.append(methods.build());
		} else {
			builder.append(generateFields());
			FieldDescriptor firstRequiredField = requiredFields.get(0);
			String builderName = Utils.firstToLower(className) + "Builder";
			builder.append(methods.has(builderName, firstRequiredField));
			builder.append(methods.requiredSetter(builderName, firstRequiredField, requiredFields));
			builder.append(methods.clear(builderName, firstRequiredField, Utils.getNextIfc(className, requiredFields, firstRequiredField)));			
		}
		return builder.toString();
	}

	private StringBuilder generatePackage() {
		return new StringBuilder("package ").append(OUTPUT_PKG).append(";");
	}

	private StringBuilder generateFields() {
		StringBuilder builder = new StringBuilder("private final ");
		builder.append(className).append(".Builder ");
		
		builder.append(className.toLowerCase()).append("Builder = ");
		builder.append(className).append(".newBuilder();");
		return builder;
	}

	private StringBuilder generateFooter() {
		return new StringBuilder("}");
	}

	private StringBuilder generateHeader() {
		StringBuilder builder = new StringBuilder("public class ").append(className).append("Builder implements ");
		builder.append(firstInterface());
		builder.append(" {");
		return builder;
	}

	private String firstInterface() {
		return requiredFields.isEmpty() ? className + "OptionalIfc" : Utils.to(requiredFields.get(0), "Ifc");
	}

	private StringBuilder generateImports() {
		StringBuilder builder = new StringBuilder();
		StringBuilder prefix = new StringBuilder("import ").append(pkg).append(".");
		builder.append(prefix).append("*;");
		builder.append(prefix).append(className).append(";");
		builder.append(prefix).append(className).append(".Builder").append(";");		
		return builder;
	}

}

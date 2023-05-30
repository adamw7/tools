package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class Clazz implements Generator {

	public static final String OUTPUT_PKG = "io.github.adamw7.tools.code";
	private final String inputClassName;
	private final String builderClassName;
	private final List<FieldDescriptor> requiredFields;
	private final String pkg;
	private final Interfaces interfaces;
	private final Implementations implementations;
	private final Methods methods;
	 
	public Clazz(Descriptor descriptor, TypeMappings typeMappings, Package pkg) {
		inputClassName = getClassName(descriptor);
		builderClassName = inputClassName + "Builder";
		requiredFields = getRequiredFields(descriptor);
		List<FieldDescriptor> mapFields = getMapFields(descriptor);
		List<FieldDescriptor> optionalFields = getOptionalFields(descriptor);
		List<FieldDescriptor> repeatedFields = getRepeatedFields(descriptor);
		
		this.pkg = pkg.getName();	
		String header = generatePackage().append(generateImports()).toString();
		this.interfaces = new Interfaces(inputClassName, optionalFields, requiredFields, mapFields, repeatedFields, typeMappings, header);
		this.implementations = new Implementations(inputClassName, optionalFields, requiredFields, mapFields, repeatedFields, typeMappings, header);
		this.methods = new Methods(typeMappings, inputClassName);
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

	@Override
	public List<ClassContainer> generate() {
		List<ClassContainer> requiredInterfaces = interfaces.generateRequired();	
		ClassContainer optionalInterface = interfaces.generateOptional();
		List<ClassContainer> requiredImpls = implementations.generateRequired();
		ClassContainer optionalImpl = implementations.generateOptional();
		
		StringBuilder full = new StringBuilder();		
		full.append(generatePackage()).append(generateImports());
		full.append(generateHeader()).append(handleMethodsInMainClass());
		full.append(generateFooter());
		
		return createClassesList(requiredInterfaces, optionalInterface, requiredImpls, optionalImpl, full);
	}

	private List<ClassContainer> createClassesList(List<ClassContainer> requiredInterfaces,
			ClassContainer optionalInterface, List<ClassContainer> requiredImpl, ClassContainer optionalImpl,
			StringBuilder full) {
		List<ClassContainer> classes = new ArrayList<>();
		classes.add(optionalInterface);
		classes.addAll(requiredInterfaces);
		classes.add(optionalImpl);
		classes.addAll(requiredImpl);
		classes.add(new ClassContainer(builderClassName, full.toString()));
				
		return classes;
	}

	private String handleMethodsInMainClass() {
		StringBuilder builder = new StringBuilder();
		if (requiredFields.isEmpty()) {
			builder.append(implementations.generateOptionalBuilderField());
			builder.append(implementations.generateOptionalBuilderConstructor(builderClassName));
			builder.append(implementations.generateMethods());
			builder.append(methods.build());
		} else {
			builder.append(generateFields());
			FieldDescriptor firstRequiredField = requiredFields.get(0);
			String builderName = Utils.firstToLower(builderClassName);
			builder.append(methods.has(builderName, firstRequiredField));
			builder.append(methods.requiredSetter(builderName, firstRequiredField, requiredFields));
			builder.append(methods.clear(builderName, firstRequiredField, Utils.getNextIfc(inputClassName, requiredFields, firstRequiredField)));			
		}
		return builder.toString();
	}

	private StringBuilder generatePackage() {
		return new StringBuilder("package ").append(OUTPUT_PKG).append(";");
	}

	private StringBuilder generateFields() {
		StringBuilder builder = new StringBuilder("private final ");
		builder.append(inputClassName).append(".Builder ");
		
		builder.append(inputClassName.toLowerCase()).append("Builder = ");
		builder.append(inputClassName).append(".newBuilder();");
		return builder;
	}

	private StringBuilder generateFooter() {
		return new StringBuilder("}");
	}

	private StringBuilder generateHeader() {
		StringBuilder builder = new StringBuilder("public class ").append(builderClassName).append(" implements ");
		builder.append(firstInterface());
		builder.append(" {");
		return builder;
	}

	private String firstInterface() {
		return requiredFields.isEmpty() ? inputClassName + "OptionalIfc" : Utils.to(requiredFields.get(0), "Ifc");
	}

	private StringBuilder generateImports() {
		StringBuilder builder = new StringBuilder();
		StringBuilder prefix = new StringBuilder("import ").append(pkg).append(".");
		builder.append(prefix).append("*;");
		builder.append(prefix).append(inputClassName).append(";");
		builder.append(prefix).append(inputClassName).append(".Builder").append(";");		
		return builder;
	}

}

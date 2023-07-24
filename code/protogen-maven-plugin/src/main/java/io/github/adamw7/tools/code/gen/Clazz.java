package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Clazz implements Generator {

	private final String builderClassName;
	private final Interfaces interfaces;
	private final Implementations implementations;
	private final Methods methods;
	private final ClassInfo info;
	 
	public Clazz(ClassInfo info, TypeMappings typeMappings) {
		this.info = info;
		builderClassName = info.name() + "Builder";
		String header = generatePackage() + generateImports();
		
		this.interfaces = new Interfaces(info, typeMappings, header);
		this.implementations = new Implementations(info, typeMappings, header);
		this.methods = new Methods(typeMappings, info.name());
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
		classes.add(new ClassContainer(builderClassName, full));
				
		return classes;
	}

	private CharSequence handleMethodsInMainClass() {
	    if (info.required().isEmpty()) {
	        return handleOptionalMethods();
	    } else {
	        return handleRequiredMethods();
	    }
	}

	private CharSequence handleOptionalMethods() {
	    StringBuilder builder = new StringBuilder();
	    builder.append(implementations.generateOptionalBuilderField());
	    builder.append(implementations.generateOptionalBuilderDefaultConstructor(builderClassName));
	    builder.append(implementations.generateOptionalBuilderConstructor(builderClassName));
	    builder.append(implementations.generateMethods());
	    builder.append(methods.build());
	    return builder;
	}

	private CharSequence handleRequiredMethods() {
	    StringBuilder builder = new StringBuilder();
	    builder.append(generateFields());

	    FieldDescriptor firstRequiredField = info.required().get(0);
	    String builderName = Utils.firstToLower(builderClassName);

	    builder.append(methods.has(builderName, firstRequiredField));
	    builder.append(methods.requiredSetter(builderName, firstRequiredField, info.required()));
	    builder.append(methods.clear(builderName, firstRequiredField, Utils.getNextIfc(info.name(), info.required(), firstRequiredField)));

	    return builder;
	}

	private String generatePackage() {
		return "package " + info.getOutputPkg() + ";";
	}

	private StringBuilder generateFields() {
		StringBuilder builder = new StringBuilder("private final ");
		builder.append(info.name()).append(".Builder ");
		
		builder.append(info.name().toLowerCase()).append("Builder = ");
		builder.append(info.name()).append(".newBuilder();");
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
		return info.name() + (info.required().isEmpty() ? "OptionalIfc" : Utils.to(info.required().get(0), "Ifc"));
	}

	private StringBuilder generateImports() {
		StringBuilder builder = new StringBuilder();
		StringBuilder prefix = new StringBuilder("import ").append(info.getInputPkg()).append(".");
		builder.append(prefix).append("*;");
		builder.append(prefix).append(info.fullName()).append(";");
		builder.append(prefix).append(info.fullName()).append(".Builder").append(";");				
		for (FieldDescriptor field : info.getGroupFields()) {
			String fieldName = Utils.toUpperCamelCase(field.getName());
			builder.append(prefix).append(info.name()).append(".").append(fieldName).append(";");
		}
		return builder;
	}

}

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
	    if (info.nonOptional().isEmpty()) {
	        return handleOptionalMethods();
	    } else {
	        return handleRequiredMethods();
	    }
	}

	private CharSequence handleOptionalMethods() {
		return new StringBuilder()
				.append(implementations.generateOptionalBuilderField())
				.append(implementations.generateOptionalBuilderDefaultConstructor(builderClassName))
				.append(implementations.generateOptionalBuilderConstructor(builderClassName))
				.append(implementations.generateMethods())
				.append(methods.build());
	}

	private CharSequence handleRequiredMethods() {
		FieldDescriptor first = info.nonOptional().getFirst();
		String builderName = Utils.firstToLower(builderClassName);
		return new StringBuilder()
				.append(generateFields())
				.append(methods.has(builderName, first))
				.append(methods.requiredSetter(builderName, first, info.nonOptional()))
				.append(methods.clear(builderName, first, Utils.getNextIfc(info.name(), info.nonOptional(), first)));
	}

	private String generatePackage() {
		return "package " + info.getOutputPkg() + ";";
	}

	private StringBuilder generateFields() {
		return new StringBuilder("private final ").append(info.name()).append(".Builder ")
				.append(Utils.firstToLower(builderClassName)).append(" = ")
				.append(info.name()).append(".newBuilder();");
	}

	private StringBuilder generateFooter() {
		return new StringBuilder("}");
	}

	private StringBuilder generateHeader() {
		return new StringBuilder("public class ").append(builderClassName).append(" implements ")
				.append(firstInterface()).append(" {");
	}

	private String firstInterface() {
		return info.name() + (info.nonOptional().isEmpty()
				? "Optional" + Utils.IFC_SUFFIX
				: Utils.to(info.nonOptional().getFirst(), Utils.IFC_SUFFIX));
	}

	private StringBuilder generateImports() {
		String prefix = "import " + info.getInputPkg() + ".";
		StringBuilder builder = new StringBuilder()
				.append(prefix).append("*;")
				.append(prefix).append(info.fullName()).append(";")
				.append(prefix).append(info.fullName()).append(".Builder;");
		for (FieldDescriptor field : info.getGroupFields()) {
			builder.append(prefix).append(info.name()).append(".")
					.append(Utils.toUpperCamelCase(field.getName())).append(";");
		}
		for (FieldDescriptor field : info.getPureComplexFields()) {
			builder.append(prefix).append(Utils.getClassName(field.toProto().getTypeName())).append(";");
		}
		return builder;
	}

}

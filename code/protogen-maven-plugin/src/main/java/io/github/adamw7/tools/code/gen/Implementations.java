package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Implementations extends AbstractStatements {

	public Implementations(ClassInfo info, TypeMappings typeMappings, String header) {
		super(info, typeMappings, header);
	}

	public List<ClassContainer> generateRequired() {
		List<ClassContainer> classes = new ArrayList<>();
		if (info.nonOptional().size() > 1) {
			for (int i = 1; i < info.nonOptional().size(); ++i) { // skipping first since already handled
				classes.add(createContainer(i));
			}
		}
		return classes;
	}

	private ClassContainer createContainer(int requiredFieldNumber) {
		String classOrBuilder = Utils.firstToLower(info.name()) + "OrBuilder";
		FieldDescriptor field = info.nonOptional().get(requiredFieldNumber);
		String prefix = info.name() + Utils.firstToUpper(field.getName());
		String ifcName = prefix + "Ifc";
		String implName = prefix + "Impl";
		StringBuilder builder = new StringBuilder(header);
		builder.append("public class ").append(implName).append(" implements ").append(ifcName).append(" {");
		builder.append("private final Builder ").append(classOrBuilder).append(";");
		builder.append(methods.constructor(implName, classOrBuilder));
		builder.append(methods.requiredSetter(classOrBuilder, field, info.nonOptional()));
		builder.append(methods.has(classOrBuilder, field));	
		String clearReturnType = Utils.getNextIfc(info.name(), info.nonOptional(), field);
		builder.append(methods.clear(classOrBuilder, field, clearReturnType));					
		builder.append("}");
		
		return new ClassContainer(implName, builder);
	}

	public ClassContainer generateOptional() {
		StringBuilder builder = new StringBuilder(header);
		builder.append("public class ");
		builder.append(optionalImplName);
		builder.append(" implements ");
		builder.append(optionalIfcName);
		builder.append(" {");
		builder.append(generateOptionalBuilderField());
		builder.append(generateOptionalBuilderConstructor(optionalImplName));
		builder.append(generateMethods());
		builder.append(methods.build());
		builder.append("}");
		return new ClassContainer(optionalImplName, builder);
	}
	
	public StringBuilder generateOptionalBuilderDefaultConstructor(String name) {
		StringBuilder builder = new StringBuilder();
		builder.append("public ").append(name);
		builder.append("() {this.builder = ");
		builder.append(info.name()).append(".newBuilder();");
		builder.append("}");
		return builder;
	}

	public StringBuilder generateOptionalBuilderConstructor(String name) {
		StringBuilder builder = new StringBuilder();
		builder.append("public ").append(name);
		builder.append("(Builder builder) {this.builder = builder;}");
		return builder;
	}

	public StringBuilder generateMethods() {
		StringBuilder builder = new StringBuilder();
		for (FieldDescriptor field : info.optional()) {
			builder.append(methods.setter(field, optionalIfcName));
			builder.append(methods.has("builder", field));
			builder.append(methods.clear("builder", field, optionalIfcName));	
		}
		return builder;
	}

	public String generateOptionalBuilderField() {
		return "private final Builder builder;";
	}
}

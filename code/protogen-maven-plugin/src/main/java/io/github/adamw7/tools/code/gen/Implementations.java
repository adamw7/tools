package io.github.adamw7.tools.code.gen;

import java.util.List;
import java.util.stream.IntStream;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;

public class Implementations extends AbstractStatements {

	public Implementations(ClassInfo info, TypeMappings typeMappings, String header) {
		super(info, typeMappings, header);
	}

	public List<ClassContainer> generateRequired() { // skipping the first since it is already handled
		return IntStream.range(1, info.nonOptional().size()).mapToObj(this::createContainer).toList();
	}

	private ClassContainer createContainer(int requiredFieldNumber) {
		String classOrBuilder = Utils.firstToLower(info.name()) + "OrBuilder";
		FieldDescriptor field = info.nonOptional().get(requiredFieldNumber);
		String prefix = info.name() + Utils.firstToUpper(field.getName());
		String ifcName = prefix + Utils.IFC_SUFFIX;
		String implName = prefix + Utils.IMPL_SUFFIX;
		StringBuilder builder = new StringBuilder(header)
				.append("public class ").append(implName).append(" implements ").append(ifcName).append(" {")
				.append("private final Builder ").append(classOrBuilder).append(";")
				.append(methods.constructor(implName, classOrBuilder))
				.append(methods.requiredSetter(classOrBuilder, field, info.nonOptional()))
				.append(methods.has(classOrBuilder, field))
				.append(methods.clear(classOrBuilder, field, Utils.getNextIfc(info.name(), info.nonOptional(), field)))
				.append("}");
		return new ClassContainer(implName, builder);
	}

	public ClassContainer generateOptional() {
		StringBuilder builder = new StringBuilder(header)
				.append("public class ").append(optionalImplName)
				.append(" implements ").append(optionalIfcName).append(" {")
				.append(generateOptionalBuilderField())
				.append(generateOptionalBuilderConstructor(optionalImplName))
				.append(generateMethods())
				.append(methods.build())
				.append("}");
		return new ClassContainer(optionalImplName, builder);
	}

	public StringBuilder generateOptionalBuilderDefaultConstructor(String name) {
		return new StringBuilder("public ").append(name)
				.append("() {this.builder = ").append(info.name()).append(".newBuilder();}");
	}

	public StringBuilder generateOptionalBuilderConstructor(String name) {
		return new StringBuilder("public ").append(name).append("(Builder builder) {this.builder = builder;}");
	}

	public StringBuilder generateMethods() {
		StringBuilder builder = new StringBuilder();
		for (FieldDescriptor field : info.optional()) {
			builder.append(methods.setter(field, optionalIfcName));
			builder.append(methods.has("builder", field));
			builder.append(methods.clear("builder", field, optionalIfcName));
		}
		for (OneofDescriptor oneof : info.realOneofs()) {
			builder.append(methods.oneofCaseGetter("builder", oneof));
			builder.append(methods.oneofClear("builder", oneof, optionalIfcName));
		}
		return builder;
	}

	public String generateOptionalBuilderField() {
		return "private final Builder builder;";
	}
}

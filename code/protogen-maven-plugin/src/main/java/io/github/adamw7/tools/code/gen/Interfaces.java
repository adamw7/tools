package io.github.adamw7.tools.code.gen;

import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;

public class Interfaces extends AbstractStatements {

	public Interfaces(ClassInfo info, TypeMappings typeMappings, String header) {
		super(info, typeMappings, header);
	}

	public List<ClassContainer> generateRequired() {
		return info.nonOptional().stream().map(this::generateInterface).toList();
	}
	
	public ClassContainer generateOptional() {
		StringBuilder builder = new StringBuilder(header)
				.append("public interface ").append(optionalIfcName).append(" {");

		for (FieldDescriptor optionalField : info.optional()) {
			builder.append(methods.declareSetter(optionalField, optionalIfcName))
					.append(methods.declareHas(optionalField))
					.append(methods.declareClear(optionalField,
							Utils.getNextIfc(info.name(), info.nonOptional(), optionalField)));
		}

		for (OneofDescriptor oneof : info.realOneofs()) {
			builder.append(methods.declareOneofCaseGetter(oneof))
					.append(methods.declareOneofClear(oneof, optionalIfcName));
		}

		builder.append(info.name()).append(" build();}");

		return new ClassContainer(optionalIfcName, builder);
	}

	private ClassContainer generateInterface(FieldDescriptor requiredField) {
		String ifcName = info.name() + Utils.to(requiredField, Utils.IFC_SUFFIX);
		String returnType = Utils.getNextIfc(info.name(), info.nonOptional(), requiredField);
		StringBuilder ifc = new StringBuilder(header)
				.append("public interface ").append(ifcName).append(" {")
				.append(methods.declareSetter(requiredField, returnType))
				.append(methods.declareHas(requiredField))
				.append(methods.declareClear(requiredField, returnType))
				.append("}");
		return new ClassContainer(ifcName, ifc);
	}
}

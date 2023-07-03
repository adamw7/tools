package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Interfaces extends AbstractStatements {

	public Interfaces(ClassInfo info, TypeMappings typeMappings, String header) {
		super(info, typeMappings, header);
	}

	public List<ClassContainer> generateRequired() {
		List<ClassContainer> ifcs = new ArrayList<>();
		
		for (FieldDescriptor requiredField : info.nonOptional()) {
			ifcs.add(generateInterface(requiredField));
		}
		return ifcs;
	}
	
	public ClassContainer generateOptional() {
		StringBuilder builder = new StringBuilder(header);
		builder.append("public interface ").append(optionalIfcName).append(" {");
		
		for (FieldDescriptor optionalField : info.optional()) {
			builder.append(methods.declareSetter(optionalField, optionalIfcName));
			builder.append(methods.declareHas(optionalField));	
			String clearReturnType = Utils.getNextIfc(info.name(), info.nonOptional(), optionalField);
			builder.append(methods.declareClear(optionalField, clearReturnType));
		}
		
		builder.append(info.name()).append(" build();}");
		
		return new ClassContainer(optionalIfcName, builder);
	}
	
	private ClassContainer generateInterface(FieldDescriptor requiredField) {
		StringBuilder ifc = new StringBuilder(header);
		ifc.append("public interface ");
		String ifcName = info.name() + Utils.to(requiredField, "Ifc");
		ifc.append(ifcName);
		ifc.append(" {");
		String returnType = Utils.getNextIfc(info.name(), info.nonOptional(), requiredField);
		ifc.append(methods.declareSetter(requiredField, returnType));
		ifc.append(methods.declareHas(requiredField));
		ifc.append(methods.declareClear(requiredField, returnType));		
		
		ifc.append("}");
				
		return new ClassContainer(ifcName, ifc);
	}
}

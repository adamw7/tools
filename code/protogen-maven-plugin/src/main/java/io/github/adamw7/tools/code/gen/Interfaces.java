package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public class Interfaces extends AbstractStatements {

	public Interfaces(String className, List<FieldDescriptor> optionalFields, List<FieldDescriptor> requiredFields,
			List<FieldDescriptor> mapFields, List<FieldDescriptor> repeatedFields, TypeMappings typeMappings, String pkg) {
		super(className, optionalFields, requiredFields, mapFields, repeatedFields, typeMappings, pkg);
	}

	public List<ClassContainer> generateRequired() {
		List<ClassContainer> ifcs = new ArrayList<>();
		
		for (FieldDescriptor requiredField : nonOptionalFields) {
			ifcs.add(generateInterface(requiredField));
		}
		return ifcs;
	}
	
	public List<ClassContainer> generateOptional() {
		StringBuilder builder = new StringBuilder(header);
		builder.append("public interface ").append(optionalIfcName).append(" {");
		
		for (FieldDescriptor optionalField : optionalFields) {
			builder.append(methods.declareSetter(optionalField, optionalIfcName));
			builder.append(methods.declareHas(optionalField));	
			String clearReturnType = Utils.getNextIfc(className, nonOptionalFields, optionalField);
			builder.append(methods.declareClear(optionalField, clearReturnType));
		}
		
		builder.append(className).append(" build();}");
		
		List<ClassContainer> ifcs = new ArrayList<>();
		ifcs.add(new ClassContainer(optionalIfcName, builder.toString()));
		return ifcs;
	}
	
	private ClassContainer generateInterface(FieldDescriptor requiredField) {
		StringBuilder ifc = new StringBuilder(header);
		ifc.append("public interface ");
		String ifcName = Utils.to(requiredField, "Ifc");
		ifc.append(ifcName);
		ifc.append(" {");
		String returnType = Utils.getNextIfc(className, nonOptionalFields, requiredField);
		ifc.append(methods.declareSetter(requiredField, returnType));
		ifc.append(methods.declareHas(requiredField));
		ifc.append(methods.declareClear(requiredField, returnType));		
		
		ifc.append("}");
				
		return new ClassContainer(ifcName, ifc.toString());
	}
}

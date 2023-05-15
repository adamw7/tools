package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public abstract class AbstractStatements {
	protected final String className;
	protected final List<FieldDescriptor> optionalFields;
	protected final List<FieldDescriptor> nonOptionalFields;
	protected final Methods methods;
	protected final String optionalIfcName;
	protected final String optionalImplName;
	
	protected AbstractStatements(String className, List<FieldDescriptor> optionalFields, List<FieldDescriptor> requiredFields, List<FieldDescriptor> mapFields, List<FieldDescriptor> repeatedFields, TypeMappings typeMappings) {
		this.className = className;
		this.optionalFields = optionalFields;
		this.nonOptionalFields = union(requiredFields, mapFields, repeatedFields);
		methods = new Methods(typeMappings, className);
		this.optionalIfcName = className + "OptionalIfc";
		this.optionalImplName = className + "OptionalImpl";				
	}
	
	protected List<FieldDescriptor> union(@SuppressWarnings("unchecked") List<FieldDescriptor>... fieldsLists) {
		List<FieldDescriptor> all = new ArrayList<>();
		for (List<FieldDescriptor> fieldsList : fieldsLists) {
			all.addAll(fieldsList);
		}
		return all;
	}

}

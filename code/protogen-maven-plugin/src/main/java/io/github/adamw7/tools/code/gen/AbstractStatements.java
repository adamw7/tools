package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors.FieldDescriptor;

public abstract class AbstractStatements {
	protected final String className;
	protected final List<FieldDescriptor> optionalFields;
	protected final List<FieldDescriptor> requiredAndMapFields;
	protected final Methods methods;
	protected final String optionalIfcName;
	protected final String optionalImplName;
	
	public AbstractStatements(String className, List<FieldDescriptor> optionalFields, List<FieldDescriptor> requiredFields, List<FieldDescriptor> mapFields, TypeMappings typeMappings) {
		this.className = className;
		this.optionalFields = optionalFields;
		this.requiredAndMapFields = union(requiredFields, mapFields);
		methods = new Methods(typeMappings, className);
		this.optionalIfcName = className + "OptionalIfc";
		this.optionalImplName = className + "OptionalImpl";				
	}
	
	protected List<FieldDescriptor> union(List<FieldDescriptor> requiredFields, List<FieldDescriptor> mapFields) {
		List<FieldDescriptor> all = new ArrayList<>();
		all.addAll(requiredFields);
		all.addAll(mapFields);
		return all;
	}

}

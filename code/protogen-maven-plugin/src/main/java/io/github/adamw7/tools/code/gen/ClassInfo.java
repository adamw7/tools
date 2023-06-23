package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class ClassInfo {
	
	private final List<FieldDescriptor> mapFields;
	private final List<FieldDescriptor> optionalFields;
	private final List<FieldDescriptor> repeatedFields;
	private final List<FieldDescriptor> requiredFields;
	private final List<FieldDescriptor> nonOptionalFields;
	
	private final Descriptor descriptor;
	private final Package pkg;

	public ClassInfo(Descriptor descriptor, Package pkg) {
		mapFields = getMapFields(descriptor);
		optionalFields = getOptionalFields(descriptor);
		repeatedFields = getRepeatedFields(descriptor);
		requiredFields = getRequiredFields(descriptor);
		this.descriptor = descriptor;
		this.nonOptionalFields = union(required(), map(), repeated());
		this.pkg = pkg;
	}
	
	protected List<FieldDescriptor> union(@SuppressWarnings("unchecked") List<FieldDescriptor>... fieldsLists) {
		List<FieldDescriptor> all = new ArrayList<>();
		for (List<FieldDescriptor> fieldsList : fieldsLists) {
			all.addAll(fieldsList);
		}
		return all;
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

	public String name() {
		return descriptor.getName();
	}

	public List<FieldDescriptor> map() {
		return mapFields;
	}

	public List<FieldDescriptor> required() {
		return requiredFields;
	}
	
	public List<FieldDescriptor> nonOptional() {
		return nonOptionalFields;
	}

	public List<FieldDescriptor> optional() {
		return optionalFields;
	}

	public List<FieldDescriptor> repeated() {
		return repeatedFields;
	}
	
	public Package pkg() {
		return pkg;
	}
}
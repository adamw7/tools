package io.github.adamw7.tools.code.gen;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class ClassInfo {

	private final List<FieldDescriptor> mapFields;
	private final List<FieldDescriptor> optionalFields;
	private final List<FieldDescriptor> repeatedFields;
	private final List<FieldDescriptor> requiredFields;
	private final List<FieldDescriptor> nonOptionalFields;
	private final List<FieldDescriptor> groupFields;

	private final Descriptor descriptor;
	private final String inputPkg;
	private final String outputPkg;

	public ClassInfo(Descriptor descriptor, String inputPkg, String outputPkg) {
		mapFields = getMapFields(descriptor);
		optionalFields = getOptionalFields(descriptor);
		repeatedFields = getRepeatedFields(descriptor);
		requiredFields = getRequiredFields(descriptor);
		groupFields = getGroupFields(descriptor);
		this.descriptor = descriptor;
		this.nonOptionalFields = union(required(), map(), repeated());
		this.inputPkg = inputPkg;
		this.outputPkg = outputPkg;
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
	
	private static List<FieldDescriptor> getPureComplexFields(Descriptor descriptor) {
		List<FieldDescriptor> allComplexFields =  descriptor.getFields().stream().filter(ClassInfo::isComplexType).toList();
		List<FieldDescriptor> pureComplexFields = new ArrayList<>();
		for (FieldDescriptor field : allComplexFields ) {
			if (isPure(field)) {
				pureComplexFields.add(field);
			}
		}
		return pureComplexFields;
	}

	private static boolean isPure(FieldDescriptor field) {
		return !isGroup(field) && !field.isMapField() && !field.isRepeated();
	}

	private static List<FieldDescriptor> getGroupFields(Descriptor descriptor) {
		List<FieldDescriptor> groups = new ArrayList<>();
		for (Descriptors.FieldDescriptor fieldDescriptor : descriptor.getFields()) {
			if (isGroup(fieldDescriptor)) {
				groups.add(fieldDescriptor);
			}
		}
		return groups;
	}

	private static boolean isGroup(Descriptors.FieldDescriptor fieldDescriptor) {
		return fieldDescriptor.getType().equals(Descriptors.FieldDescriptor.Type.GROUP);
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

	public List<FieldDescriptor> getOptionalFields() {
		return optionalFields;
	}

	public List<FieldDescriptor> getGroupFields() {
		return groupFields;
	}

	public String getOutputPkg() {
		return outputPkg;
	}

	public String getInputPkg() {
		return inputPkg;
	}

	public String fullName() {
		return Utils.getClassName(descriptor.getFullName());
	}
	
	public List<FieldDescriptor> getPureComplexFields() {
		return getPureComplexFields(descriptor);
	}

	private static boolean isComplexType(Descriptors.FieldDescriptor fieldDescriptor) {
        return switch (fieldDescriptor.getType()) {
            case INT32, INT64, UINT32, UINT64, SINT32, SINT64, FIXED32, FIXED64, SFIXED32, SFIXED64, BOOL, FLOAT, DOUBLE, STRING, BYTES, ENUM ->
                    false;
            default -> true;
        };
    }
}

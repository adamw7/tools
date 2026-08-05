package io.github.adamw7.tools.code.gen;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;

public class ClassInfo {

	private final List<FieldDescriptor> mapFields;
	private final List<FieldDescriptor> optionalFields;
	private final List<FieldDescriptor> repeatedFields;
	private final List<FieldDescriptor> requiredFields;
	private final List<FieldDescriptor> nonOptionalFields;
	private final List<FieldDescriptor> groupFields;
	private final List<FieldDescriptor> pureComplexFields;

	private final Descriptor descriptor;
	private final String inputPkg;
	private final String outputPkg;

	public ClassInfo(Descriptor descriptor, String inputPkg, String outputPkg) {
		mapFields = fieldsMatching(descriptor, FieldDescriptor::isMapField);
		optionalFields = fieldsMatching(descriptor, ClassInfo::hasOptionalLabel);
		repeatedFields = fieldsMatching(descriptor, ClassInfo::isPureRepeated);
		requiredFields = fieldsMatching(descriptor, FieldDescriptor::isRequired);
		groupFields = fieldsMatching(descriptor, ClassInfo::isGroup);
		pureComplexFields = fieldsMatching(descriptor, field -> isComplexType(field) && isPure(field));
		this.descriptor = descriptor;
		this.nonOptionalFields = Stream.of(required(), map(), repeated()).flatMap(List::stream).toList();
		this.inputPkg = inputPkg;
		this.outputPkg = outputPkg;
	}

	/** The descriptor's fields the predicate accepts, in declaration order — how every field group here is derived. */
	private static List<FieldDescriptor> fieldsMatching(Descriptor descriptor, Predicate<FieldDescriptor> accepted) {
		return descriptor.getFields().stream().filter(accepted).toList();
	}

	private static boolean isPureRepeated(FieldDescriptor field) {
		return field.isRepeated() && !field.isMapField();
	}

	private static boolean hasOptionalLabel(FieldDescriptor field) {
		return field.toProto().getLabel() == FieldDescriptorProto.Label.LABEL_OPTIONAL;
	}

	private static boolean isPure(FieldDescriptor field) {
		return !isGroup(field) && !field.isMapField() && !field.isRepeated();
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

	public List<OneofDescriptor> realOneofs() {
		return descriptor.getRealOneofs();
	}
	
	public List<FieldDescriptor> getPureComplexFields() {
		return pureComplexFields;
	}

	private static boolean isComplexType(Descriptors.FieldDescriptor fieldDescriptor) {
        return switch (fieldDescriptor.getType()) {
            case INT32, INT64, UINT32, UINT64, SINT32, SINT64, FIXED32, FIXED64, SFIXED32, SFIXED64, BOOL, FLOAT, DOUBLE, STRING, BYTES, ENUM ->
                    false;
            default -> true;
        };
    }
}

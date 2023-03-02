package io.github.adamw7.tools.code;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.GeneratedMessageV3;

public class Code {

	private final String outputDir;
	private TypeMappings typeMappings;

	public Code() {
		outputDir = "target/generated-sources/";
		createPkg(Clazz.OUTPUT_PKG);
	}

	private void createPkg(String pkg) {
		File dir = new File(outputDir + "/" + pkg);
		dir.delete();
		dir.mkdirs();
	}

	public void genBuilders(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		typeMappings = new TypeMappings(allMessages);
		for (Class<? extends GeneratedMessageV3> c : allMessages) {
			try {
				write(genBuilder(c), c.getSimpleName() + "Builder");
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
	}

	private void write(String code, String className) {
		try (FileWriter myWriter = new FileWriter(outputDir + Clazz.OUTPUT_PKG + "/" + className + ".java")) {
			myWriter.write(code);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private String genBuilder(Class<? extends GeneratedMessageV3> c)
			throws NoSuchMethodException, SecurityException, IllegalAccessException, InvocationTargetException {
		Method getDescriptorMethod = c.getDeclaredMethod("getDescriptor");
		Object object = getDescriptorMethod.invoke(this);
		if (object == null) {
			throw new IllegalStateException("getDescriptor method return null");
		}
		if (object instanceof Descriptor) {
			Descriptor descriptor = (Descriptor) object;
			return genBuilder(descriptor, c.getPackage());
		} else {
			throw new IllegalStateException("Wrong return type of the getDescriptor method: " + object.getClass());
		}
	}

	private String genBuilder(Descriptor descriptor, Package pkg) {
		Clazz clazz = new Clazz(descriptor, typeMappings, pkg);
		return clazz.generate();
	}

}

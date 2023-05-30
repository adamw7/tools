package io.github.adamw7.tools.code.gen;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.GeneratedMessageV3;

import io.github.adamw7.tools.code.format.Formatter;

public class Code {

	private final static Logger log = LogManager.getLogger(Code.class.getName());
	
	private final String generatedSourcesDir;
	private TypeMappings typeMappings;

	public Code(String generatedSourcesDir) {
		this.generatedSourcesDir = generatedSourcesDir;
		createPkg(Clazz.OUTPUT_PKG);
	}

	private void createPkg(String pkg) {
		String directory = generatedSourcesDir + "/" + pkg;
		File dir = new File(directory);
		dir.delete();
		try {
			Files.createDirectories(replace(directory));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		log.info("{} created", dir);
	}

	private Path replace(String pkg) {
		return Paths.get(pkg.replaceAll("\\.", "/"));
	}

	public void genBuilders(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		typeMappings = new TypeMappings(allMessages);
		for (Class<? extends GeneratedMessageV3> c : allMessages) {
			try {
				List<ClassContainer> classes = genBuilder(c);
				for (ClassContainer container : classes) {					
					String generatedCode = new Formatter().format(container.code());
					write(generatedCode, container.name());					
				}
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
				log.error(e);
			}
		}
	}

	private void write(String code, String className) {
		try (FileWriter myWriter = new FileWriter(generatedSourcesDir + replace(Clazz.OUTPUT_PKG) + "/" + className + ".java")) {
			myWriter.write(code);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private List<ClassContainer> genBuilder(Class<? extends GeneratedMessageV3> c)
			throws NoSuchMethodException, SecurityException, IllegalAccessException, InvocationTargetException {
		Method getDescriptorMethod = c.getDeclaredMethod("getDescriptor");
		Object object = getDescriptorMethod.invoke(this);
		if (object == null) {
			throw new IllegalStateException("getDescriptor method return null");
		}
		if (object instanceof Descriptor descriptor) {
			checkSyntax(descriptor);
			return genBuilder(descriptor, c.getPackage());
		} else {
			throw new IllegalStateException("Wrong return type of the getDescriptor method: " + object.getClass());
		}
	}

	private void checkSyntax(Descriptor descriptor) {
		FileDescriptor.Syntax syntax = descriptor.getFile().getSyntax();
		if (!syntax.equals(FileDescriptor.Syntax.PROTO2)) {
			throw new IllegalStateException("Only proto2 syntax supported. The input contains: " + syntax);
		}
	}

	private List<ClassContainer> genBuilder(Descriptor descriptor, Package pkg) {
		Clazz clazz = new Clazz(descriptor, typeMappings, pkg);
		return clazz.generate();
	}

}

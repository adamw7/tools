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

public class Code {

	private final static Logger log = LogManager.getLogger(Code.class.getName());
	
	private final String generatedSourcesDir;
	private TypeMappings typeMappings;
	private final String outputPkg;

	public Code(String generatedSourcesDir, String outputPkg) {
		this.generatedSourcesDir = generatedSourcesDir;
		this.outputPkg = outputPkg;
		createPkg(outputPkg);
	}

	private void createPkg(String pkg) {
		String directory = generatedSourcesDir + File.separator + pkg;
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
					write(container.format());					
				}
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
				log.error(e);
			}
		}
	}

	private void write(ClassContainer container) {
		String fileName = generatedSourcesDir + replace(outputPkg + File.separator + container.name()) + ".java";
		try (FileWriter myWriter = new FileWriter(fileName)) {
			log.info("Writing {}", fileName);
			myWriter.write(container.codeAsString());
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

	private List<ClassContainer> genBuilder(Descriptor descriptor, Package inputPkg) {
		Clazz clazz = new Clazz(new ClassInfo(descriptor, inputPkg.getName(), outputPkg), typeMappings);
		return clazz.generate();
	}

}

package io.github.adamw7.tools.code;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.GeneratedMessageV3;

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
		log.info(dir + " created");
	}

	private Path replace(String pkg) {
		return Paths.get(pkg.replaceAll("\\.", "/"));
	}

	public void genBuilders(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		typeMappings = new TypeMappings(allMessages);
		for (Class<? extends GeneratedMessageV3> c : allMessages) {
			try {
				write(genBuilder(c), c.getSimpleName() + "Builder");
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

	private String genBuilder(Class<? extends GeneratedMessageV3> c)
			throws NoSuchMethodException, SecurityException, IllegalAccessException, InvocationTargetException {
		Method getDescriptorMethod = c.getDeclaredMethod("getDescriptor");
		Object object = getDescriptorMethod.invoke(this);
		if (object == null) {
			throw new IllegalStateException("getDescriptor method return null");
		}
		if (object instanceof Descriptor descriptor) {
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

package io.github.adamw7.tools.code.gen;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.GeneratedMessage;

import io.github.adamw7.tools.code.MojoException;

public class Code {

	private static final Logger log = LogManager.getLogger(Code.class.getName());
	
	private final String generatedSourcesDir;
	private TypeMappings typeMappings;
	private final String outputPkg;
	private final Path outputDir;

	public Code(String generatedSourcesDir, String outputPkg) {
		this.generatedSourcesDir = generatedSourcesDir;
		this.outputPkg = outputPkg;
		this.outputDir = createPkg(outputPkg);
	}

	private Path createPkg(String pkg) {
		String directory = generatedSourcesDir + File.separator + pkg;
		Path dir = pkgToPath(directory);

		try {
			deleteRecursively(dir);
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		log.info("{} created", directory);
		return dir;
	}

	private void deleteRecursively(Path path) throws IOException {
		if (Files.notExists(path)) {
			return;
		}
		if (Files.isDirectory(path)) {
			deleteChildren(path);
		}
		Files.delete(path);
	}

	private void deleteChildren(Path directory) throws IOException {
		try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
			for (Path child : children) {
				deleteRecursively(child);
			}
		}
	}

	private Path pkgToPath(String pkg) {
		return Paths.get(pkg.replaceAll("\\.", "/"));
	}

	public void genBuilders(Set<Class<? extends GeneratedMessage>> allMessages) {
		typeMappings = new TypeMappings(allMessages);
		for (Class<? extends GeneratedMessage> c : allMessages) {
			try {
				List<ClassContainer> classes = genBuilder(c);
				for (ClassContainer container : classes) {
					write(container.format());
				}
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
				throw new MojoException(e);
			}
		}
	}

	private void write(ClassContainer container) {
		Path file = outputDir.resolve(container.name() + ".java");
		try (FileWriter myWriter = new FileWriter(file.toFile(), StandardCharsets.UTF_8)) {
			log.info("Writing {}", file);
			myWriter.write(container.codeAsString());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private List<ClassContainer> genBuilder(Class<? extends GeneratedMessage> c)
			throws NoSuchMethodException, SecurityException, IllegalAccessException, InvocationTargetException {
		Method getDescriptorMethod = c.getDeclaredMethod("getDescriptor");
		Object object = getDescriptorMethod.invoke(null);
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
		FileDescriptorProto proto = descriptor.getFile().toProto();
		String syntax = proto.getSyntax();
		if (!"proto2".equals(syntax) && !"proto3".equals(syntax) && !syntax.isEmpty()) {
			throw new IllegalStateException("Only proto2 and proto3 syntax are supported. The input contains: " + syntax);
		}
	}

	private List<ClassContainer> genBuilder(Descriptor descriptor, Package inputPkg) {
		Clazz clazz = new Clazz(new ClassInfo(descriptor, inputPkg.getName(), outputPkg), typeMappings);
		return clazz.generate();
	}

}

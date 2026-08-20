package io.github.adamw7.tools.code.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.Edition;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.GeneratedMessage;

/**
 * The contracts {@link Code} states in its own guard clauses, driven through
 * synthetic descriptors rather than through the {@code .proto} fixtures.
 *
 * <p>Every guard here is a refusal, and a refusal needs a message that violates
 * it. protoc will not produce one — it emits only valid proto2, proto3 and
 * editions files, and an editions fixture added to {@code src/test/proto} would
 * be picked up by every other test's package scan and fail those instead. So the
 * message classes below are stubs: abstract, never instantiated, and reached the
 * way {@code Code} reaches a real one, by invoking a static {@code getDescriptor}
 * reflectively. That is enough to hand the generator a descriptor no fixture
 * could carry.
 */
public class CodeTest {

	private static final String OUTPUT_PKG = "com/x/out";

	/** Carries the syntax {@code Code} accepts today, so the accepting branch is asserted and not assumed. */
	public abstract static class Proto2Message extends GeneratedMessage {
		public static Descriptor getDescriptor() {
			return descriptorWith("proto2");
		}
	}

	/** A file written before {@code syntax} was declared at all, which is proto2 by definition. */
	public abstract static class UndeclaredSyntaxMessage extends GeneratedMessage {
		public static Descriptor getDescriptor() {
			return descriptorWith("");
		}
	}

	/** protoc emits this for an {@code edition = "2023"} file — a real syntax the generator does not support. */
	public abstract static class EditionsMessage extends GeneratedMessage {
		public static Descriptor getDescriptor() {
			return descriptorWith("editions");
		}
	}

	public abstract static class UnknownSyntaxMessage extends GeneratedMessage {
		public static Descriptor getDescriptor() {
			return descriptorWith("proto1");
		}
	}

	public abstract static class NullDescriptorMessage extends GeneratedMessage {
		public static Descriptor getDescriptor() {
			return null;
		}
	}

	public abstract static class WrongDescriptorTypeMessage extends GeneratedMessage {
		public static Object getDescriptor() {
			return "not a descriptor";
		}
	}

	@Test
	public void generatesTheBuilderTrioForASupportedSyntax(@TempDir Path generatedSources) throws IOException {
		generate(generatedSources, Proto2Message.class);

		assertEquals(List.of("SampleBuilder.java", "SampleOptionalIfc.java", "SampleOptionalImpl.java"),
				generatedFileNames(generatedSources));
	}

	@Test
	public void acceptsAFileThatDeclaresNoSyntax(@TempDir Path generatedSources) throws IOException {
		generate(generatedSources, UndeclaredSyntaxMessage.class);

		assertFalse(generatedFileNames(generatedSources).isEmpty(),
				"an absent syntax means proto2, which the generator supports");
	}

	@Test
	public void refusesEditionsSyntax(@TempDir Path generatedSources) {
		IllegalStateException refusal = assertThrows(IllegalStateException.class,
				() -> generate(generatedSources, EditionsMessage.class));

		assertEquals("Only proto2 and proto3 syntax are supported. The input contains: editions",
				refusal.getMessage());
	}

	@Test
	public void refusesUnrecognisedSyntax(@TempDir Path generatedSources) {
		IllegalStateException refusal = assertThrows(IllegalStateException.class,
				() -> generate(generatedSources, UnknownSyntaxMessage.class));

		assertEquals("Only proto2 and proto3 syntax are supported. The input contains: proto1",
				refusal.getMessage());
	}

	@Test
	public void refusesAMessageWhoseDescriptorIsNull(@TempDir Path generatedSources) {
		IllegalStateException refusal = assertThrows(IllegalStateException.class,
				() -> generate(generatedSources, NullDescriptorMessage.class));

		assertEquals("getDescriptor method return null", refusal.getMessage());
	}

	@Test
	public void refusesAMessageWhoseDescriptorIsOfTheWrongType(@TempDir Path generatedSources) {
		IllegalStateException refusal = assertThrows(IllegalStateException.class,
				() -> generate(generatedSources, WrongDescriptorTypeMessage.class));

		assertTrue(refusal.getMessage().startsWith("Wrong return type of the getDescriptor method:"),
				"the refusal names the type that was returned; was: " + refusal.getMessage());
	}

	@Test
	public void writesEveryGeneratedSourceWithContent(@TempDir Path generatedSources) throws IOException {
		generate(generatedSources, Proto2Message.class);

		for (Path source : generatedFiles(generatedSources)) {
			String code = Files.readString(source);
			assertFalse(code.isBlank(), source.getFileName() + " was written empty");
			assertTrue(code.startsWith("package com.x.out;"),
					source.getFileName() + " must open with its package declaration; was: " + firstLine(code));
		}
	}

	/**
	 * A builder removed from the {@code .proto} must not survive in the output
	 * directory, where it would go on compiling and hide the removal — the reason
	 * the output package is cleared rather than overwritten.
	 */
	@Test
	public void clearsStaleOutputBeforeGenerating(@TempDir Path generatedSources) throws IOException {
		Path outputDir = generatedSources.resolve(OUTPUT_PKG);
		Path nested = outputDir.resolve("nested");
		Files.createDirectories(nested);
		Path staleBuilder = outputDir.resolve("RemovedMessageBuilder.java");
		Path staleNested = nested.resolve("AlsoRemoved.java");
		Files.writeString(staleBuilder, "public class RemovedMessageBuilder {}");
		Files.writeString(staleNested, "public class AlsoRemoved {}");

		generate(generatedSources, Proto2Message.class);

		assertFalse(Files.exists(staleBuilder), "a builder left from an earlier run must not survive");
		assertFalse(Files.exists(staleNested), "the clear-out must reach nested directories too");
		assertFalse(Files.exists(nested), "the nested directory itself must go with its contents");
	}

	private void generate(Path generatedSources, Class<? extends GeneratedMessage> message) {
		new Code(mock(Log.class), generatedSources.toString(), OUTPUT_PKG.replace('/', '.'))
				.genBuilders(Set.of(message));
	}

	private List<String> generatedFileNames(Path generatedSources) throws IOException {
		return generatedFiles(generatedSources).stream().map(path -> path.getFileName().toString()).sorted().toList();
	}

	private List<Path> generatedFiles(Path generatedSources) throws IOException {
		Path outputDir = generatedSources.resolve(OUTPUT_PKG);
		if (Files.notExists(outputDir)) {
			return List.of();
		}
		try (Stream<Path> entries = Files.list(outputDir)) {
			return entries.filter(Files::isRegularFile).toList();
		}
	}

	private static String firstLine(String code) {
		return code.lines().findFirst().orElse("");
	}

	/**
	 * A one-message file carrying exactly the syntax asked for. The edition has to
	 * be set alongside {@code syntax = "editions"} or protobuf rejects the file
	 * before {@link Code} ever sees it.
	 */
	private static Descriptor descriptorWith(String syntax) {
		FileDescriptorProto.Builder file = FileDescriptorProto.newBuilder()
				.setName("synthetic-" + (syntax.isEmpty() ? "undeclared" : syntax) + ".proto")
				.setPackage("synthetic")
				.addMessageType(DescriptorProto.newBuilder().setName("Sample"));
		if (!syntax.isEmpty()) {
			file.setSyntax(syntax);
		}
		if ("editions".equals(syntax)) {
			file.setEdition(Edition.EDITION_2023);
		}
		try {
			return FileDescriptor.buildFrom(file.build(), new FileDescriptor[0]).getMessageTypes().getFirst();
		} catch (DescriptorValidationException e) {
			throw new IllegalStateException("Could not build a synthetic " + syntax + " descriptor", e);
		}
	}
}

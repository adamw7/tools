package io.github.adamw7.context.okf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OkfBundleWriterTest {

	@TempDir
	Path target;

	private final OkfBundleWriter writer = new OkfBundleWriter();

	@Test
	void writesEveryDocumentAtItsBundleRelativePath() throws IOException {
		OkfBundle bundle = new OkfBundle(List.of(new OkfDocument("index.md", "root"),
				new OkfDocument("pkg/A.java.md", "concept")));

		writer.write(bundle, target);

		assertEquals("root", Files.readString(target.resolve("index.md")));
		assertEquals("concept", Files.readString(target.resolve("pkg/A.java.md")));
	}

	@Test
	void createsTheDirectoriesADocumentNeeds() {
		OkfBundle bundle = new OkfBundle(List.of(new OkfDocument("one/two/three/A.java.md", "deep")));

		writer.write(bundle, target);

		assertTrue(Files.isDirectory(target.resolve("one/two/three")));
	}

	@Test
	void returnsTheBundleRootItWroteTo() {
		assertEquals(target, writer.write(new OkfBundle(List.of()), target));
	}

	@Test
	void writesDocumentsAsUtf8() throws IOException {
		OkfBundle bundle = new OkfBundle(List.of(new OkfDocument("index.md", "wgłębienie")));

		writer.write(bundle, target);

		assertEquals("wgłębienie",
				new String(Files.readAllBytes(target.resolve("index.md")), StandardCharsets.UTF_8));
	}

	@Test
	void reportsAnUnwritableTargetRatherThanFailingSilently() throws IOException {
		Path file = Files.createFile(target.resolve("occupied"));
		OkfBundle bundle = new OkfBundle(List.of(new OkfDocument("index.md", "root")));

		assertThrows(UncheckedIOException.class, () -> writer.write(bundle, file));
	}
}

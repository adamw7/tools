package io.github.adamw7.tools.adopt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutableResolverTest {

	private static final List<String> EXTENSIONS = List.of(".com", ".exe", ".bat", ".cmd");

	@Test
	void posixReturnsTheCommandUnchanged(@TempDir Path dir) throws IOException {
		Files.createFile(dir.resolve("mvn.cmd"));
		ExecutableResolver resolver = new ExecutableResolver(false, List.of(dir), EXTENSIONS);
		List<String> command = List.of("mvn", "-N", "validate");
		assertSame(command, resolver.resolve(command));
	}

	@Test
	void windowsRewritesABatchScriptThroughCmd(@TempDir Path dir) throws IOException {
		Path script = Files.createFile(dir.resolve("mvn.cmd"));
		ExecutableResolver resolver = new ExecutableResolver(true, List.of(dir), EXTENSIONS);
		assertEquals(List.of("cmd.exe", "/c", script.toString(), "-N", "validate"),
				resolver.resolve(List.of("mvn", "-N", "validate")));
	}

	@Test
	void windowsRewritesARealExecutableToItsAbsolutePath(@TempDir Path dir) throws IOException {
		Path executable = Files.createFile(dir.resolve("git.exe"));
		ExecutableResolver resolver = new ExecutableResolver(true, List.of(dir), EXTENSIONS);
		assertEquals(List.of(executable.toString(), "clone", "https://example.test/repo.git"),
				resolver.resolve(List.of("git", "clone", "https://example.test/repo.git")));
	}

	@Test
	void windowsPrefersAnExecutableExtensionOverABareFileOfTheSameName(@TempDir Path dir) throws IOException {
		Files.createFile(dir.resolve("tool"));
		Path executable = Files.createFile(dir.resolve("tool.exe"));
		ExecutableResolver resolver = new ExecutableResolver(true, List.of(dir), EXTENSIONS);
		assertEquals(List.of(executable.toString()), resolver.resolve(List.of("tool")));
	}

	@Test
	void windowsSearchesPathDirectoriesInOrder(@TempDir Path first, @TempDir Path second) throws IOException {
		Files.createFile(first.resolve("gh.exe"));
		Files.createFile(second.resolve("gh.exe"));
		ExecutableResolver resolver = new ExecutableResolver(true, List.of(first, second), EXTENSIONS);
		assertEquals(List.of(first.resolve("gh.exe").toString()), resolver.resolve(List.of("gh")));
	}

	@Test
	void windowsLeavesAnUnresolvableCommandUnchanged(@TempDir Path dir) {
		ExecutableResolver resolver = new ExecutableResolver(true, List.of(dir), EXTENSIONS);
		List<String> command = List.of("definitely-not-installed");
		assertSame(command, resolver.resolve(command));
	}

	@Test
	void windowsLeavesAProgramThatAlreadyCarriesAPathUnchanged(@TempDir Path dir) throws IOException {
		Files.createFile(dir.resolve("mvn.cmd"));
		ExecutableResolver resolver = new ExecutableResolver(true, List.of(dir), EXTENSIONS);
		List<String> command = List.of("some/dir/mvn", "-N");
		assertSame(command, resolver.resolve(command));
	}

	@Test
	void anEmptyCommandIsReturnedUnchanged() {
		ExecutableResolver resolver = new ExecutableResolver(true, List.of(), EXTENSIONS);
		List<String> command = List.of();
		assertSame(command, resolver.resolve(command));
	}
}

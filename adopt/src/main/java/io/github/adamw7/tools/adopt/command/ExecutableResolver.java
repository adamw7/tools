package io.github.adamw7.tools.adopt.command;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.adamw7.tools.adopt.Platform;

/**
 * Resolves a command's program name to a form {@link ProcessBuilder} can actually
 * launch on the host operating system.
 *
 * <p>On POSIX the command is returned unchanged. On Windows a bare program name
 * such as {@code mvn} or {@code claude} routinely resolves to a {@code .cmd} or
 * {@code .bat} shim, which {@code CreateProcess} refuses to start. This resolver
 * searches the {@code PATH} using {@code PATHEXT} and rewrites a batch script to
 * run through {@code cmd.exe /c}, a real executable to its absolute path. A
 * program that cannot be located is returned unchanged, so the caller still fails
 * with its usual "could not start" error.
 *
 * <p>Only the program name is ever routed through {@code cmd.exe}; the arguments
 * reach {@link ProcessBuilder} unchanged, so free-form arguments such as a
 * pull-request title never reach the command interpreter.
 */
final class ExecutableResolver {

	private static final List<String> BATCH_EXTENSIONS = List.of(".cmd", ".bat");
	private static final List<String> DEFAULT_PATH_EXTENSIONS = List.of(".com", ".exe", ".bat", ".cmd");

	private final boolean windows;
	private final List<Path> pathDirectories;
	private final List<String> pathExtensions;

	ExecutableResolver() {
		this(Platform.isWindows(), pathDirectoriesFromEnvironment(), pathExtensionsFromEnvironment());
	}

	ExecutableResolver(boolean windows, List<Path> pathDirectories, List<String> pathExtensions) {
		this.windows = windows;
		this.pathDirectories = List.copyOf(pathDirectories);
		this.pathExtensions = List.copyOf(pathExtensions);
	}

	List<String> resolve(List<String> command) {
		if (!windows || command.isEmpty()) {
			return command;
		}
		return executable(command.get(0))
				.map(resolved -> rewrite(resolved, command))
				.orElse(command);
	}

	private Optional<Path> executable(String program) {
		return hasPathSeparator(program) ? given(program) : locate(program);
	}

	/**
	 * A program named by its path needs no {@code PATH} search, but a batch script at
	 * that path still has to go through {@code cmd.exe} — which is how a checkout's
	 * own {@code gradlew.bat} or {@code mvnw.cmd} is launched. Anything else is left
	 * for {@link ProcessBuilder} to start as it was given.
	 */
	private Optional<Path> given(String program) {
		return toPath(program).filter(Files::isRegularFile).filter(this::isBatchScript);
	}

	private Optional<Path> locate(String program) {
		return pathDirectories.stream()
				.flatMap(directory -> candidates(directory, program))
				.filter(Files::isRegularFile)
				.findFirst();
	}

	private Stream<Path> candidates(Path directory, String program) {
		Stream<Path> withExtensions = pathExtensions.stream().map(extension -> directory.resolve(program + extension));
		Stream<Path> bareName = Stream.of(directory.resolve(program));
		return Stream.concat(withExtensions, bareName);
	}

	private List<String> rewrite(Path executable, List<String> command) {
		List<String> rewritten = new ArrayList<>();
		if (isBatchScript(executable)) {
			rewritten.add("cmd.exe");
			rewritten.add("/c");
		}
		rewritten.add(executable.toString());
		rewritten.addAll(command.subList(1, command.size()));
		return rewritten;
	}

	private boolean isBatchScript(Path executable) {
		String name = executable.getFileName().toString().toLowerCase(Locale.ROOT);
		return BATCH_EXTENSIONS.stream().anyMatch(name::endsWith);
	}

	private boolean hasPathSeparator(String program) {
		return program.indexOf('/') >= 0 || program.indexOf('\\') >= 0;
	}

	private static List<Path> pathDirectoriesFromEnvironment() {
		return splitPathList(System.getenv("PATH")).stream()
				.map(ExecutableResolver::toPath)
				.flatMap(Optional::stream)
				.toList();
	}

	private static Optional<Path> toPath(String entry) {
		try {
			return Optional.of(Path.of(entry));
		} catch (InvalidPathException e) {
			return Optional.empty();
		}
	}

	private static List<String> pathExtensionsFromEnvironment() {
		List<String> configured = splitPathList(System.getenv("PATHEXT")).stream()
				.map(extension -> extension.startsWith(".") ? extension : "." + extension)
				.map(extension -> extension.toLowerCase(Locale.ROOT))
				.toList();
		return configured.isEmpty() ? DEFAULT_PATH_EXTENSIONS : configured;
	}

	private static List<String> splitPathList(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return Arrays.stream(value.split(Pattern.quote(File.pathSeparator)))
				.filter(entry -> !entry.isBlank())
				.toList();
	}
}

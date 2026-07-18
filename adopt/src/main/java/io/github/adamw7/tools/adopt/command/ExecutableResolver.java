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

/**
 * Resolves a command's program name to a form {@link ProcessBuilder} can
 * actually launch on the host operating system.
 *
 * <p>On POSIX the command is returned unchanged. On Windows a bare program name
 * such as {@code mvn} or {@code claude} routinely resolves to a {@code .cmd} or
 * {@code .bat} shim, which the underlying {@code CreateProcess} call cannot
 * start: it appends {@code .exe} to an extensionless name and refuses to run a
 * batch script directly, so a bare {@code mvn} fails with "Cannot run program".
 * This resolver searches the {@code PATH} using {@code PATHEXT} and, when the
 * program is a batch script, rewrites the command to run through
 * {@code cmd.exe /c}; a real executable is rewritten to its resolved absolute
 * path. A program that cannot be located is returned unchanged, so the caller
 * still fails with its usual "could not start" error rather than being masked
 * here.
 *
 * <p>Only the program name is ever routed through {@code cmd.exe}; the arguments
 * are handed to {@link ProcessBuilder} unchanged. Free-form arguments such as a
 * pull-request title or body therefore never reach the command interpreter,
 * because {@code git} and {@code gh} resolve to real {@code .exe} files rather
 * than to batch scripts.
 */
final class ExecutableResolver {

	private static final List<String> BATCH_EXTENSIONS = List.of(".cmd", ".bat");
	private static final List<String> DEFAULT_PATH_EXTENSIONS = List.of(".com", ".exe", ".bat", ".cmd");

	private final boolean windows;
	private final List<Path> pathDirectories;
	private final List<String> pathExtensions;

	ExecutableResolver() {
		this(isWindows(), pathDirectoriesFromEnvironment(), pathExtensionsFromEnvironment());
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
		return locate(command.get(0))
				.map(executable -> rewrite(executable, command))
				.orElse(command);
	}

	private Optional<Path> locate(String program) {
		if (hasPathSeparator(program)) {
			return Optional.empty();
		}
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

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
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

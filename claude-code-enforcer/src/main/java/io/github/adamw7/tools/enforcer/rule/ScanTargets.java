package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * The {@code files} plus {@code directories} pair of parameters shared by the
 * rules that check a set of paths rather than one document. Either parameter may
 * be left unconfigured, but a rule with neither has nothing to check, which is a
 * build-setup mistake.
 * <p>
 * Directory contents are returned in sorted order, because {@link Files#walk}
 * yields entries in a filesystem-dependent order that would otherwise let a rule
 * report its violations differently run to run. An absent directory is skipped,
 * since most Claude Code configuration directories are optional.
 * <p>
 * The two parameters overlap freely — naming a file explicitly and scanning the
 * directory it sits in is a natural way to say "this one especially" — so
 * {@link #allFiles} yields each file once. A rule that checked both lists in turn
 * would report the overlapping file's violations twice and record them twice in a
 * baseline.
 */
public final class ScanTargets {

	private final List<File> files;
	private final List<File> directories;

	public ScanTargets(List<File> files, List<File> directories) {
		this.files = files != null ? files : List.of();
		this.directories = directories != null ? directories : List.of();
	}

	/** Fails when neither parameter names anything to check. */
	public void requireConfigured() throws EnforcerRuleException {
		if (files.isEmpty() && directories.isEmpty()) {
			throw new EnforcerRuleException("Configure at least one of the files or directories parameters");
		}
	}

	/** The explicitly configured files, in the order they were configured. */
	public List<File> files() {
		return files;
	}

	/**
	 * Every configured file, then every regular file under the configured
	 * directories, each listed once.
	 */
	public List<File> allFiles() {
		return allFiles(path -> true);
	}

	/**
	 * Every configured file, then the files under the configured directories that
	 * {@code acceptedInDirectories} matches, each listed once. The predicate narrows
	 * the directory scan alone: a file named explicitly was chosen by the
	 * configuration and is checked whatever its name.
	 */
	public List<File> allFiles(Predicate<Path> acceptedInDirectories) {
		Map<Path, File> unique = new LinkedHashMap<>();
		files.forEach(file -> unique.putIfAbsent(key(file), file));
		filesInDirectories(acceptedInDirectories).forEach(file -> unique.putIfAbsent(key(file), file));
		return List.copyOf(unique.values());
	}

	private static Path key(File file) {
		return ProjectFiles.normalized(file);
	}

	/** The regular files under the configured directories that {@code accepted} matches. */
	public List<File> filesInDirectories(Predicate<Path> accepted) {
		return directories.stream()
				.filter(File::isDirectory)
				.flatMap(directory -> walk(directory, accepted).stream())
				.toList();
	}

	private List<File> walk(File directory, Predicate<Path> accepted) {
		try (Stream<Path> walk = Files.walk(directory.toPath())) {
			return walk.filter(Files::isRegularFile).filter(accepted).sorted().map(Path::toFile).toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not scan directory " + directory, e);
		}
	}
}

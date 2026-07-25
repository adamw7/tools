package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

	/** Every regular file under the configured directories. */
	public List<File> filesInDirectories() {
		return filesInDirectories(path -> true);
	}

	/** The regular files under the configured directories that {@code accepted} matches. */
	public List<File> filesInDirectories(Predicate<Path> accepted) {
		List<File> found = new ArrayList<>();
		for (File directory : directories) {
			if (directory.isDirectory()) {
				found.addAll(walk(directory, accepted));
			}
		}
		return found;
	}

	private List<File> walk(File directory, Predicate<Path> accepted) {
		try (Stream<Path> walk = Files.walk(directory.toPath())) {
			return walk.filter(Files::isRegularFile).filter(accepted).sorted().map(Path::toFile).toList();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not scan directory " + directory, e);
		}
	}
}

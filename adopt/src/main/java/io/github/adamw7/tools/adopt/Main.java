package io.github.adamw7.tools.adopt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;

/**
 * Command-line entry point: given a GitHub repository URL (and an optional
 * workspace directory to clone into), runs the default adoption pipeline
 * against a real {@code git}/{@code claude} toolchain. A supplied workspace
 * directory is created when it does not yet exist, so the clone step always has
 * a directory to run in; when omitted, a temporary one is created instead.
 */
public class Main {

	public static void main(String[] args) {
		String repositoryUrl = repositoryUrl(args);
		Path workspace = workspace(args);
		CommandRunner runner = new ProcessCommandRunner();
		GitHubRepoAdopter.withDefaultPipeline(runner).adopt(new AdoptionContext(repositoryUrl, workspace));
	}

	private static String repositoryUrl(String[] args) {
		if (args == null || args.length < 1 || args[0].isBlank()) {
			throw new IllegalArgumentException("Usage: <github-repo-url> [workspace-directory]");
		}
		return args[0];
	}

	static Path workspace(String[] args) {
		if (args.length >= 2 && !args[1].isBlank()) {
			return createIfMissing(Path.of(args[1]));
		}
		return createTemporaryWorkspace();
	}

	private static Path createIfMissing(Path workspace) {
		try {
			return Files.createDirectories(workspace);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path createTemporaryWorkspace() {
		try {
			return Files.createTempDirectory("claude-adopt-");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
